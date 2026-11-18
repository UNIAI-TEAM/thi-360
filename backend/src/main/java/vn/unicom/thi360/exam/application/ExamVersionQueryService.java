package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.ExamVersionChange;
import vn.unicom.thi360.exam.api.ExamVersionList;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamVersionPath;
import vn.unicom.thi360.exam.api.ExamVersionResponse;
import vn.unicom.thi360.exam.api.PersonRef;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.identity.spi.UserDirectory;

@Service
public class ExamVersionQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogLookup catalog;
    private final SolverLimits limits;

    public ExamVersionQueryService(JdbcTemplate jdbc, ObjectMapper mapper, CatalogLookup catalog,
            SolverLimits limits) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalog = catalog;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public ExamVersionList list(UUID examId, ExamDetailResponse exam) {
        var rows = readRows(jdbc, examId);
        List<ExamVersionResponse> result = exam.versions().stream()
                .filter(version -> version.version() != null).toList();
        ExamVersionList.WorkingCopy working = null;
        List<ExamVersionChange> changes = List.of();
        if (exam.hasUnpublishedChanges()) {
            JsonNode parts = json(jdbc.queryForObject(
                    "select parts::text from exam.exam_structure where exam_id = ?", String.class, examId));
            JsonNode config = currentConfig(examId);
            Set<String> alreadyPublished = new LinkedHashSet<>();
            for (Row row : rows) {
                JsonNode oldConfig = json(row.config());
                if ("SERVING".equals(row.state())
                        && ExamVersionChanges.between(oldConfig, json(row.parts()),
                                comparisonConfig(oldConfig, config, parts, row.servingPaths()), parts).isEmpty()) {
                    alreadyPublished.addAll(row.servingPaths());
                }
            }
            if (!rows.isEmpty()) {
                Row base = rows.stream().filter(r -> java.util.Objects.equals(r.version(), exam.servingVersion()))
                        .findFirst().orElse(rows.getFirst());
                JsonNode oldConfig = json(base.config());
                changes = ExamVersionChanges.between(oldConfig, json(base.parts()),
                        comparisonConfig(oldConfig, config, parts, base.servingPaths()), parts);
            }
            UUID kind = config.hasNonNull("kindItemId") ? UUID.fromString(config.path("kindItemId").asText()) : null;
            List<ExamVersionPath> unpublished = enumerate(parts.path("parts"), kind).stream()
                    .filter(path -> !alreadyPublished.contains(path.pathId())).toList();
            working = new ExamVersionList.WorkingCopy(exam.updatedBy(), exam.updatedAt(),
                    exam.servingVersion(), null, exam.check(), unpublished);
        }
        return new ExamVersionList(examId, working, changes, List.copyOf(result));
    }

    /** Published rows shared by exam detail and version-list responses. */
    public static List<ExamVersionResponse> publishedRows(JdbcTemplate jdbc, ObjectMapper mapper,
            UserDirectory users, UUID examId) {
        var rows = readRows(jdbc, examId);
        var people = users.findByIds(rows.stream().map(Row::publishedBy).distinct().toList());
        List<ExamVersionResponse> result = new ArrayList<>();
        for (Row row : rows) {
            var user = people.get(row.publishedBy());
            PersonRef publisher = new PersonRef(row.publishedBy(), user == null ? null : user.displayName());
            try {
                JsonNode parts = mapper.readTree(row.parts()).path("parts");
                result.add(new ExamVersionResponse(row.version(), row.state(), row.publishedAt(), publisher, 0,
                        row.note(), paths(parts, jsonNode(mapper, row.config()), row.publishedPaths()),
                        paths(parts, jsonNode(mapper, row.config()), row.servingPaths())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid exam version snapshot", ex);
            }
        }
        return List.copyOf(result);
    }

    private static List<Row> readRows(JdbcTemplate jdbc, UUID examId) {
        return jdbc.query("select version_no, state, published_at, published_by, note, parts::text, "
                        + "published_path_ids, serving_path_ids, config::text from exam.exam_version where exam_id = ? order by version_no desc",
                (rs, n) -> new Row(rs.getInt(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        List.of((String[]) rs.getArray(7).getArray()), List.of((String[]) rs.getArray(8).getArray()),
                        rs.getString(9)), examId);
    }

    private static List<ExamVersionPath> paths(JsonNode parts, JsonNode config, List<String> ids) {
        List<ExamVersionPath> result = new ArrayList<>();
        for (String id : ids) {
            List<String> selected = Arrays.asList(id.split("\\+", -1));
            List<ExamVersionPath.Choice> choices = new ArrayList<>();
            for (JsonNode part : parts) {
                for (JsonNode option : part.path("options")) {
                    if (selected.contains(option.path("id").asText())) {
                        choices.add(new ExamVersionPath.Choice(part.path("id").asText(), option.path("id").asText(),
                                option.path("kind").asText(null), option.path("code").asText(null), option.path("name").asText(null)));
                    }
                }
                for (JsonNode combination : config.path("combinations")) {
                    String partId = part.path("id").asText();
                    String code = combination.path("code").asText();
                    String optionId = partId + ":" + code;
                    if (partId.equals(combination.path("partId").asText()) && selected.contains(optionId)) {
                        choices.add(new ExamVersionPath.Choice(partId, optionId, "COMBINATION", code,
                                combination.path("name").asText(code)));
                    }
                }
            }
            result.add(new ExamVersionPath(id, List.copyOf(choices)));
        }
        return List.copyOf(result);
    }

    private static JsonNode jsonNode(ObjectMapper mapper, String value) {
        try {
            return mapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid exam version snapshot", ex);
        }
    }

    private JsonNode comparisonConfig(JsonNode old, JsonNode current, JsonNode parts, List<String> paths) {
        var config = ((com.fasterxml.jackson.databind.node.ObjectNode) current).deepCopy();
        var snapshot = new ExamVersionSnapshot(mapper);
        if (old.has("combinations")) {
            UUID kind = config.hasNonNull("kindItemId") ? UUID.fromString(config.path("kindItemId").asText()) : null;
            config.set("combinations", snapshot.combinations(parts, Set.copyOf(paths), kind, catalog).path("combinations"));
        }
        if (old.has("omittedGroups")) config.set("omittedGroups", snapshot.omittedGroups(parts));
        if (!old.has("examQuotas")) config.remove("examQuotas");
        return config;
    }

    private JsonNode currentConfig(UUID examId) {
        String config = jdbc.queryForObject("""
                select jsonb_build_object('code', e.code, 'name', e.name, 'description', e.description,
                'instructions', e.instructions, 'kindItemId', e.kind_item_id, 'scope', e.scope,
                'partCode', e.part_code, 'subjectItemId', e.subject_item_id, 'templateCode', e.template_code,
                'structureYear', e.structure_year, 'matrixYear', e.matrix_year, 'yearsConfirmed', e.years_confirmed,
                'timing', e.timing, 'durationMinutes', e.duration_minutes, 'maxScore', e.max_score,
                'referenceMax', e.reference_max, 'practiceScoring', e.practice_scoring, 'coverAssetId', e.cover_asset_id,
                'kindProfile', jsonb_build_object('scopes', p.scopes, 'templateBy', p.template_by, 'timing', p.timing,
                'maxScore', p.max_score, 'referenceMax', p.reference_max, 'practiceScoring', p.practice_scoring,
                'trueFalseScoring', p.tf_scoring, 'multipleChoiceRule', p.mc_rule),
                'examQuotas', s.exam_quotas)::text
                from exam.exam e join exam.exam_structure s on s.exam_id=e.id
                left join exam.exam_kind_profile p on p.exam_item_id=e.kind_item_id where e.id=?
                """, String.class, examId);
        return json(config);
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid exam version snapshot", ex);
        }
    }

    /**
     * Same path set as the check. When the live product exceeds the solver path limit the check reports
     * PATH_LIMIT_EXCEEDED with no paths, so nothing is publishable: return an empty list before materializing it.
     */
    private List<ExamVersionPath> enumerate(JsonNode parts, UUID kind) {
        List<CatalogCombinationView> definitions = null;
        List<JsonNode> orderedParts = new ArrayList<>();
        parts.forEach(orderedParts::add);
        orderedParts.sort(java.util.Comparator.comparing(
                part -> part.path("order").isNumber() ? part.path("order").intValue() : null,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        List<List<ExamVersionPath.Choice>> partOptions = new ArrayList<>();
        List<Integer> partChoose = new ArrayList<>();
        java.math.BigInteger total = java.math.BigInteger.ONE;
        for (JsonNode part : orderedParts) {
            boolean codeSource = part.hasNonNull("codeSource");
            if (!codeSource && !"CHOICE".equals(part.path("participation").path("mode").asText())) continue;
            String partId = part.path("id").asText();
            List<ExamVersionPath.Choice> options = new ArrayList<>();
            int choose;
            if (codeSource) {
                // Same expansion as the solver and ExamVersionSnapshot.combinations: one choice per catalog combination.
                choose = 1;
                if (definitions == null) definitions = catalog.listCombinations(kind);
                for (CatalogCombinationView definition : definitions) {
                    if (!ExamVersionSnapshot.inBranch(part, definition)) continue;
                    options.add(new ExamVersionPath.Choice(partId, partId + ":" + definition.itemCode(), "COMBINATION",
                            definition.itemCode(), definition.itemNameVi()));
                }
            } else {
                choose = part.path("participation").path("choose").asInt();
                for (JsonNode option : part.path("options")) {
                    options.add(new ExamVersionPath.Choice(partId, option.path("id").asText(),
                            option.path("kind").asText(null), option.path("code").asText(null), option.path("name").asText(null)));
                }
            }
            total = total.multiply(waysToChoose(options.size(), choose));
            if (total.signum() == 0) return List.of();
            if (total.compareTo(java.math.BigInteger.valueOf(limits.maxPaths())) > 0) return List.of();
            partOptions.add(options);
            partChoose.add(choose);
        }
        List<List<ExamVersionPath.Choice>> combinations = new ArrayList<>();
        combinations.add(new ArrayList<>());
        for (int i = 0; i < partOptions.size(); i++) {
            List<List<ExamVersionPath.Choice>> picks = new ArrayList<>();
            choose(partOptions.get(i), partChoose.get(i), 0, new ArrayList<>(), picks);
            List<List<ExamVersionPath.Choice>> product = new ArrayList<>();
            for (var prefix : combinations) for (var pick : picks) {
                List<ExamVersionPath.Choice> next = new ArrayList<>(prefix);
                next.addAll(pick);
                product.add(next);
            }
            combinations = product;
        }
        return combinations.stream().map(choices -> new ExamVersionPath(
                choices.isEmpty() ? "default" : choices.stream().map(ExamVersionPath.Choice::optionId)
                        .collect(java.util.stream.Collectors.joining("+")), List.copyOf(choices))).toList();
    }

    /** Number of picks {@link #choose} would generate: C(n, k), 0 when k is below 1 or above n. */
    private static java.math.BigInteger waysToChoose(int n, int k) {
        if (k < 1 || k > n) return java.math.BigInteger.ZERO;
        java.math.BigInteger ways = java.math.BigInteger.ONE;
        int m = Math.min(k, n - k);
        for (int i = 1; i <= m; i++) {
            ways = ways.multiply(java.math.BigInteger.valueOf(n - m + i)).divide(java.math.BigInteger.valueOf(i));
        }
        return ways;
    }

    private static void choose(List<ExamVersionPath.Choice> options, int count, int start,
            List<ExamVersionPath.Choice> selected, List<List<ExamVersionPath.Choice>> result) {
        if (count < 1 && selected.isEmpty()) return;
        if (count == 0) {
            result.add(List.copyOf(selected));
            return;
        }
        for (int i = start; i <= options.size() - count; i++) {
            selected.add(options.get(i));
            choose(options, count - 1, i + 1, selected, result);
            selected.removeLast();
        }
    }

    private record Row(int version, String state, Instant publishedAt, UUID publishedBy, String note,
                       String parts, List<String> publishedPaths, List<String> servingPaths, String config) {}
}
