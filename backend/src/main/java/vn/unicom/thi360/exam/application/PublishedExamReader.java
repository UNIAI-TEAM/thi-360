package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PublishedExamPort;

/**
 * {@link PublishedExamPort} over {@code exam.exam_version}. Everything comes from the frozen snapshot (config and
 * parts), including the kind code of versions published after EX-1; the only live reads are the exam status, read in
 * the same statement as the serving versions so one answer never mixes two moments, and the catalog code of the exam
 * kind for a version that predates {@code kindCode} in the snapshot. The parts are projected, not copied: blocks,
 * quotas, matrices, knowledge scopes and witnesses stay behind.
 */
@Service
public class PublishedExamReader implements PublishedExamPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogLookup catalog;
    private final ExamCoverService covers;

    public PublishedExamReader(JdbcTemplate jdbc, ObjectMapper mapper, CatalogLookup catalog, ExamCoverService covers) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalog = catalog;
        this.covers = covers;
    }

    private record Row(int version, String state, String status, Instant publishedAt, String config, String parts,
                       List<String> publishedPaths, List<String> servingPaths, String examStatus) { }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServingExam> findServing(UUID examId) {
        // One statement, one snapshot: the exam status and the serving versions cannot come from different moments.
        List<Row> serving = rows("WHERE v.exam_id = ? AND v.state = 'SERVING' AND e.status <> 'DRAFT' ORDER BY v.version_no DESC", examId);
        if (serving.isEmpty()) return Optional.empty();
        PublishedVersion latest = version(examId, serving.getFirst());
        Map<String, ServingPath> paths = new LinkedHashMap<>();
        for (Row row : serving) {
            JsonNode parts = json(row.parts()).path("parts");
            JsonNode config = json(row.config());
            for (String pathId : row.servingPaths()) {
                paths.putIfAbsent(pathId, servingPath(row, parts, config, pathId));
            }
        }
        return Optional.of(new ServingExam(examId, serving.getFirst().examStatus(), latest, List.copyOf(paths.values())));
    }

    /** A serving path from the version that serves it: choices, totals, per-part totals and its combinations (R2-4). */
    private ServingPath servingPath(Row row, JsonNode parts, JsonNode config, String pathId) {
        List<ServingPathPart> pathParts = new ArrayList<>();
        boolean complete = true;
        Integer questions = 0;
        BigDecimal points = BigDecimal.ZERO;
        Integer minutes = 0;
        for (JsonNode part : parts) {
            JsonNode entry = null;
            for (JsonNode candidate : part.path("paths")) {
                if (pathId.equals(candidate.path("pathId").asText())) entry = candidate;
            }
            if (entry == null) {
                complete = false;
                continue;
            }
            Integer partQuestions = integer(entry, "questionCount");
            BigDecimal partPoints = decimal(entry, "maxPoints");
            Integer partMinutes = integer(entry, "durationMinutes");
            pathParts.add(new ServingPathPart(text(part, "id"), text(part, "name"), partQuestions, partPoints, partMinutes));
            questions = questions == null || partQuestions == null ? null : questions + partQuestions;
            points = points == null || partPoints == null ? null : points.add(partPoints);
            minutes = minutes == null || partMinutes == null ? null : minutes + partMinutes;
        }
        if (!complete || pathParts.isEmpty()) {
            questions = null;
            points = null;
            minutes = null;
        }
        // A SHARED clock is the exam's; the snapshot only repeats it on a single-part exam.
        if ("SHARED".equals(text(config, "timing")) && minutes == null && complete) minutes = integer(config, "durationMinutes");
        List<String> selected = Arrays.asList(pathId.split("\\+", -1));
        List<Combination> combinations = new ArrayList<>();
        for (JsonNode combination : config.path("combinations")) {
            if (selected.contains(combination.path("partId").asText() + ":" + combination.path("code").asText())) {
                combinations.add(combination(combination));
            }
        }
        return new ServingPath(pathId, row.version(), choices(parts, config, pathId), questions, points, minutes,
                List.copyOf(pathParts), List.copyOf(combinations));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedVersion> loadVersion(UUID examId, int versionNo) {
        return rows("WHERE v.exam_id = ? AND v.version_no = " + versionNo, examId).stream().findFirst()
                .map(row -> version(examId, row));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listPublishedExamIds() {
        return jdbc.queryForList("SELECT e.id FROM exam.exam e WHERE e.status IN ('PUBLISHED', 'DISCONTINUED') "
                + "AND EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.exam_id = e.id) ORDER BY e.code", UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CoverImage> openCover(UUID assetId) {
        return covers.open(assetId).map(cover -> new CoverImage(cover.contentType(), cover.content()));
    }

    private List<Row> rows(String where, UUID examId) {
        return jdbc.query("SELECT v.version_no, v.state, v.status, v.published_at, v.config::text, v.parts::text, "
                        + "v.published_path_ids, v.serving_path_ids, e.status FROM exam.exam_version v "
                        + "JOIN exam.exam e ON e.id = v.exam_id " + where,
                (rs, n) -> new Row(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                        rs.getString(5), rs.getString(6), Arrays.asList((String[]) rs.getArray(7).getArray()),
                        Arrays.asList((String[]) rs.getArray(8).getArray()), rs.getString(9)), examId);
    }

    private PublishedVersion version(UUID examId, Row row) {
        JsonNode config = json(row.config());
        JsonNode parts = json(row.parts()).path("parts");
        UUID kind = uuid(config, "kindItemId");
        // Frozen at publish; only a version published before that falls back to the catalog.
        String kindCode = config.hasNonNull("kindCode") ? config.path("kindCode").asText()
                : kind == null ? null : catalog.findItem(kind).map(CatalogItemView::code).orElse(null);
        List<Part> mappedParts = new ArrayList<>();
        for (JsonNode part : parts) mappedParts.add(part(part));
        List<Combination> combinations = new ArrayList<>();
        for (JsonNode combination : config.path("combinations")) combinations.add(combination(combination));
        List<OmittedGroup> omitted = new ArrayList<>();
        for (JsonNode group : config.path("omittedGroups")) omitted.add(new OmittedGroup(text(group, "name"), decimal(group, "points")));
        return new PublishedVersion(examId, row.version(), row.state(), row.status(), row.publishedAt(),
                text(config, "code"), text(config, "name"), kind, kindCode, text(config, "scope"), text(config, "partCode"),
                uuid(config, "subjectItemId"), config.hasNonNull("buildMode") ? config.path("buildMode").asText() : "STANDARD",
                text(config, "timing"), integer(config, "durationMinutes"), decimal(config, "maxScore"),
                decimal(config, "referenceMax"), text(config, "practiceScoring"), listing(config),
                List.copyOf(mappedParts), List.copyOf(combinations), List.copyOf(omitted),
                List.copyOf(row.publishedPaths()), List.copyOf(row.servingPaths()), text(config, "instructions"));
    }

    private Listing listing(JsonNode config) {
        JsonNode listing = config.path("listing");
        if (!listing.isObject()) return null;
        return new Listing(text(listing, "summary"), text(listing, "objectives"), text(listing, "audience"),
                listing.hasNonNull("listPriceVnd") ? listing.path("listPriceVnd").asLong() : null,
                integer(listing, "attemptsGranted"), listing.path("benefitAiExplanation").asBoolean(false),
                listing.path("benefitStrengthWeakness").asBoolean(false), listing.path("benefitPractice").asBoolean(false),
                text(listing, "benefitNote"), text(listing, "resultView"), text(listing, "description"),
                uuid(listing, "coverAssetId"));
    }

    private Part part(JsonNode part) {
        List<Segment> segments = new ArrayList<>();
        for (JsonNode segment : part.path("displaySegments")) {
            segments.add(new Segment(text(segment, "code"), text(segment, "name"), integer(segment, "fromPosition"),
                    integer(segment, "toPosition"), integer(segment, "commandCount"), decimal(segment, "maxPoints")));
        }
        List<Option> options = new ArrayList<>();
        for (JsonNode option : part.path("options")) {
            List<Integer> counts = null;
            if (option.path("countsByPick").isArray()) {
                counts = new ArrayList<>();
                for (JsonNode count : option.path("countsByPick")) counts.add(count.asInt());
            }
            options.add(new Option(text(option, "id"), text(option, "kind"), text(option, "code"), text(option, "name"),
                    integer(option, "chooseSubjects"), counts == null ? null : List.copyOf(counts), sections(option.path("sections"))));
        }
        List<BlockSummary> blocks = new ArrayList<>();
        for (JsonNode section : part.path("sections")) addSectionBlocks(blocks, section, null);
        for (JsonNode section : part.path("subjectPool")) addSectionBlocks(blocks, section, null);
        for (JsonNode option : part.path("options")) {
            String optionId = text(option, "id");
            addBlocks(blocks, option.path("blocks"), null, optionId, null);
            for (JsonNode section : option.path("sections")) addSectionBlocks(blocks, section, optionId);
        }
        List<PartPath> paths = new ArrayList<>();
        for (JsonNode path : part.path("paths")) {
            paths.add(new PartPath(text(path, "pathId"), integer(path, "questionCount"), decimal(path, "maxPoints"),
                    integer(path, "durationMinutes")));
        }
        JsonNode participation = part.path("participation");
        return new Part(text(part, "id"), integer(part, "order"), text(part, "name"), integer(part, "durationMinutes"),
                integer(part, "questionCount"), integer(part, "questionCountOnPaper"), integer(part, "questionCountRequired"),
                text(participation, "mode"), integer(participation, "choose"), decimal(part, "maxPoints"),
                List.copyOf(segments), sections(part.path("sections")), sections(part.path("subjectPool")),
                List.copyOf(options), List.copyOf(paths), List.copyOf(blocks));
    }

    /**
     * The blocks of one section (or of one option, with no section) as answer-type rows (AD-77, AD-78). The count is the
     * frozen allocation: the block's own {@code questionCount}, else {@code setCount * setSize}, else (a "take the
     * remainder" block) the section's target minus the fixed blocks. A block whose count cannot be derived is left out,
     * never guessed. An omitted block is carried with {@code omitted = true} and its configured count, so the reader of
     * the port can leave it out of what the learner answers; it never counts as fixed for the remainder.
     */
    private static void addBlocks(List<BlockSummary> target, JsonNode blocks, Integer sectionTarget, String optionId,
            String sectionId) {
        int fixed = 0;
        int remainders = 0;
        for (JsonNode block : blocks) {
            if (block.path("omitted").asBoolean(false)) continue;
            Integer own = ownCount(block);
            if (own != null) fixed += own; else if (!isSet(block)) remainders++;
        }
        for (JsonNode block : blocks) {
            boolean omitted = block.path("omitted").asBoolean(false);
            Integer count = ownCount(block);
            if (count == null && !omitted && !isSet(block) && remainders == 1 && sectionTarget != null) {
                count = Math.max(0, sectionTarget - fixed);
            }
            if (count == null || count <= 0) continue;
            target.add(new BlockSummary(text(block, "questionType"), count, text(block, "childAnswerType"), optionId,
                    sectionId, omitted));
        }
    }

    private static void addSectionBlocks(List<BlockSummary> target, JsonNode section, String optionId) {
        addBlocks(target, section.path("blocks"), integer(section, "questionCount"), optionId, text(section, "id"));
    }

    /** The count a block states itself: {@code questionCount}, else {@code setCount * setSize}; null when it states none. */
    private static Integer ownCount(JsonNode block) {
        if (block.path("questionCount").isIntegralNumber()) return block.path("questionCount").intValue();
        if (block.path("setCount").isIntegralNumber() && block.path("setSize").isIntegralNumber()) {
            return block.path("setCount").intValue() * block.path("setSize").intValue();
        }
        return null;
    }

    private static boolean isSet(JsonNode block) {
        String type = block.path("questionType").asText("");
        return "QUESTION_SET".equals(type) || "CLOZE".equals(type);
    }

    private static List<Section> sections(JsonNode sections) {
        List<Section> result = new ArrayList<>();
        for (JsonNode section : sections) {
            result.add(new Section(text(section, "id"), text(section, "subjectCode"), integer(section, "questionCount"),
                    section.hasNonNull("audioRequirement") ? section.path("audioRequirement").asText() : "ANY"));
        }
        return List.copyOf(result);
    }

    private static Combination combination(JsonNode combination) {
        List<CombinationSubject> subjects = new ArrayList<>();
        for (JsonNode subject : combination.path("subjects")) {
            subjects.add(new CombinationSubject(text(subject, "code"), text(subject, "name"), subject.path("position").asInt()));
        }
        return new Combination(text(combination, "partId"), text(combination, "code"), text(combination, "name"),
                text(combination, "branch"), integer(combination, "versionNo"), List.copyOf(subjects));
    }

    /** The choices a path stands for: its options by id, and its catalog codes as {@code partId:code}. */
    private static List<PathChoice> choices(JsonNode parts, JsonNode config, String pathId) {
        List<String> selected = Arrays.asList(pathId.split("\\+", -1));
        List<PathChoice> choices = new ArrayList<>();
        for (JsonNode part : parts) {
            String partId = part.path("id").asText();
            for (JsonNode option : part.path("options")) {
                if (selected.contains(option.path("id").asText())) {
                    choices.add(new PathChoice(partId, text(option, "id"), text(option, "kind"), text(option, "code"), text(option, "name")));
                }
            }
            for (JsonNode combination : config.path("combinations")) {
                String code = combination.path("code").asText();
                if (partId.equals(combination.path("partId").asText()) && selected.contains(partId + ":" + code)) {
                    choices.add(new PathChoice(partId, partId + ":" + code, "COMBINATION", code,
                            combination.hasNonNull("name") ? combination.path("name").asText() : code));
                }
            }
        }
        return List.copyOf(choices);
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid exam version snapshot", e);
        }
    }

    private static String text(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.path(name).asText() : null;
    }

    private static Integer integer(JsonNode node, String name) {
        return node.path(name).isIntegralNumber() ? node.path(name).intValue() : null;
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        return node.path(name).isNumber() ? node.path(name).decimalValue() : null;
    }

    private static UUID uuid(JsonNode node, String name) {
        return node.hasNonNull(name) ? UUID.fromString(node.path(name).asText()) : null;
    }
}
