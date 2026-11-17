package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.exam.api.ExamPreviewController.*;
import vn.unicom.thi360.exam.domain.*;
import vn.unicom.thi360.exam.domain.solver.*;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Seeded generation for one requested path, using serving question snapshots only. */
@Service
public class ExamPreviewService {
    private final ExamPreviewStore store;
    private final SolverInputBuilder builder;
    private final SolverLimits limits;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CatalogLookup catalog;

    public ExamPreviewService(ExamPreviewStore store, SolverInputBuilder builder, SolverLimits limits,
            ObjectMapper mapper, Clock clock, CatalogLookup catalog) {
        this.store = store;
        this.builder = builder;
        this.limits = limits;
        this.mapper = mapper;
        this.clock = clock;
        this.catalog = catalog;
    }

    public PreviewResult generate(UUID id, GeneratePreviewRequest request) {
        var snapshot = request.version() == null ? store.load(id)
                : store.loadVersion(id, request.version(), request.pathId());
        ExamStructureDocument document = new ExamStructureCodec(mapper).read(snapshot.parts());
        var selectedIds = new java.util.HashSet<>(List.of(request.pathId().split("\\+", -1)));
        List<ObjectNode> draftIssues = new ExamCheckRules(mapper, snapshot.exam(), document).unrepresentable()
                .stream().filter(issue -> {
                    String optionId = issue.path("optionId").asText(null);
                    return optionId == null || selectedIds.contains(optionId);
                }).toList();
        if (!draftIssues.isEmpty()) {
            PathRef path = PreviewPathResolver.resolveDraft(document, request.pathId(), request.subjectCodes());
            return new PreviewResult(id, path, request.version(), request.seed(), Instant.now(clock), "FAILED", null,
                    "INVALID", draftIssues.stream().map(issue -> mapper.convertValue(issue, PreviewIssue.class)).toList(),
                    null, null, null);
        }
        PaperSolver solver = new PaperSolver(limits, clock);
        SolverInputBuilder.SolverInput input;
        try {
            input = builder.build(id, maskUnselectedOptions(document, request.pathId()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
        }
        var resolved = PreviewPathResolver.resolve(document, input.parts(), request.pathId(),
                request.subjectCodes(), request.seed(), solver.allocator());
        PathResult selectedPath = new PathResult(resolved.path(), SolveStatus.VALID, null, null,
                null, List.of(), List.of(), null);
        List<ObjectNode> staticIssues = ExamCheckRules.issuesForPath(mapper, snapshot.exam(),
                document, selectedPath);
        if (staticIssues.stream().anyMatch(issue -> !"WARNING".equals(issue.path("severity").asText()))) {
            String reason = staticIssues.stream().anyMatch(issue -> "ERROR".equals(issue.path("severity").asText()))
                    ? "INVALID" : "UNCONFIRMED";
            return new PreviewResult(id, resolved.pathRef(), request.version(), request.seed(), Instant.now(clock),
                    "FAILED", null, reason,
                    staticIssues.stream().map(issue -> mapper.convertValue(issue, PreviewIssue.class)).toList(),
                    null, null, null);
        }
        PathResult result = solver.solvePath(resolved.path(), input.candidates(), SolveMode.generate(request.seed()));
        if (result.status() != SolveStatus.VALID) {
            return new PreviewResult(id, resolved.pathRef(), request.version(), request.seed(), Instant.now(clock),
                    "FAILED", null, result.status().name(), issues(result, document), null, null, null);
        }
        Map<String, GroupWitness> groups = new HashMap<>();
        List<QuestionSourcePort.VersionKey> keys = new ArrayList<>();
        for (SubjectWitness subject : result.witness()) {
            for (GroupWitness group : subject.groups()) {
                groups.put(group.groupId(), group);
                for (QuestionPick pick : group.picks()) keys.add(key(pick));
            }
        }
        var candidates = input.candidates().loadedCandidates();
        List<QuestionSourcePort.VersionKey> snapshotKeys = new ArrayList<>(keys);
        for (var key : keys) {
            var candidate = candidates.get(key);
            if (candidate == null) throw new IllegalStateException("Selected candidate missing");
            if (candidate.setId() != null) {
                for (var unit : candidate.units()) {
                    if (unit.questionId() != null && !unit.questionId().equals(candidate.questionId())) {
                        snapshotKeys.add(new QuestionSourcePort.VersionKey(unit.questionId(), key.versionNo()));
                    }
                }
            }
        }
        var versions = store.loadVersions(snapshotKeys);
        Map<String, SectionOnPath> sections = new HashMap<>();
        for (SectionOnPath section : resolved.path().sections()) sections.put(section.sectionId(), section);
        for (List<SubjectPick> choices : resolved.path().picks()) {
            for (SubjectPick pick : choices) for (SectionOnPath section : pick.resolvedSections()) sections.put(section.sectionId(), section);
        }
        PaperBuilder paperBuilder = new PaperBuilder(groups, candidates, versions, sections,
                catalog.listGroupItems("KNOWLEDGE"));
        PreviewPaper paper = paperBuilder.build(snapshot, document, resolved.path());
        store.recordPreview(id, snapshot.code(), request.pathId(), request.seed(), keys);
        return new PreviewResult(id, resolved.pathRef(), request.version(), request.seed(), Instant.now(clock),
                "GENERATED", paper, null, null, null, null, null);
    }


    private static QuestionSourcePort.VersionKey key(QuestionPick pick) {
        return new QuestionSourcePort.VersionKey(UUID.fromString(pick.questionId()), pick.versionNo());
    }

    /** Keep unrelated incomplete draft options from preventing the requested path being built. */
    private static ExamStructureDocument maskUnselectedOptions(ExamStructureDocument document, String pathId) {
        var selected = new java.util.HashSet<>(List.of(pathId.split("\\+", -1)));
        List<ExamPart> parts = new ArrayList<>(document.parts().size());
        for (ExamPart part : document.parts()) {
            List<ExamPartOption> options = new ArrayList<>(part.options().size());
            for (ExamPartOption option : part.options()) {
                options.add(selected.contains(option.id()) ? option
                        : new ExamPartOption(option.id(), OptionKind.SUBJECT, option.code(), option.name(),
                                null, null, List.of(), List.of(), option.expertConfirmed()));
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                    part.questionCountOnPaper(), part.questionCountRequired(), part.participation(),
                    part.maxPoints(), part.sections(), options));
        }
        return new ExamStructureDocument(parts);
    }

    /**
     * Typed issues of a FAILED preview (spec 5.3: "đường không hợp lệ thì trả FAILED kèm lỗi"):
     * one issue per failed subject-choice selection, else one for the path-level reason; the
     * mapping matches {@link ExamCheckSolverIssues} so the web panel renders them like check issues.
     */
    private List<PreviewIssue> issues(PathResult result, ExamStructureDocument document) {
        var solverIssues = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document,
                limits.timeout().toSeconds());
        Map<String, ObjectNode> found = new LinkedHashMap<>();
        for (SelectionResult selection : result.selections()) {
            if (selection.status() != SolveStatus.VALID) {
                ObjectNode issue = solverIssues.failure(selection.reason(), selection.subjectCode(),
                        selection.groupId(), selection.failingQuota(), selection.picks());
                found.putIfAbsent(issue.path("key").asText(), issue);
            }
        }
        if (found.isEmpty() && result.reason() != null) {
            ObjectNode issue = solverIssues.failure(result.reason(), result.subjectCode(),
                    result.groupId(), result.failingQuota(), result.failingSelections());
            found.putIfAbsent(issue.path("key").asText(), issue);
        }
        return found.values().stream().map(node -> mapper.convertValue(node, PreviewIssue.class)).toList();
    }

    private static final class PaperBuilder {
        private final Map<String, GroupWitness> witnesses;
        private final Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates;
        private final Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions;
        private final Map<String, SectionOnPath> sections;
        private final List<SubjectAllocation> subjects = new ArrayList<>();
        private final Map<UUID, CatalogItemView> knowledge;
        private final Map<UUID, KnowledgeStat> knowledgeStats = new LinkedHashMap<>();
        private int number = 1;
        private BigDecimal totalPoints = BigDecimal.ZERO;
        private boolean hasSet;
        private boolean manual;

        PaperBuilder(Map<String, GroupWitness> witnesses,
                Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
                Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions,
                Map<String, SectionOnPath> sections, List<CatalogItemView> knowledge) {
            this.witnesses = witnesses;
            this.candidates = candidates;
            this.versions = versions;
            this.sections = sections;
            this.knowledge = new HashMap<>();
            for (CatalogItemView node : knowledge) this.knowledge.put(node.id(), node);
        }

        PreviewPaper build(ExamPreviewStore.Snapshot exam, ExamStructureDocument doc, SolvePath path) {
            List<PreviewPart> parts = new ArrayList<>();
            int partMinutes = 0;
            List<ExamPart> ordered = doc.parts().stream().sorted(Comparator.comparing(ExamPart::order,
                    Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            for (ExamPart part : ordered) {
                int first = number;
                BigDecimal before = totalPoints;
                List<PreviewBlock> blocks = new ArrayList<>();
                for (ExamSection section : part.sections()) appendSection(section, null, blocks);
                for (ChosenOption selected : path.chosen()) {
                    if (!part.id().equals(selected.partId())) continue;
                    ExamPartOption option = part.options().stream().filter(o -> selected.optionId().equals(o.id())).findFirst().orElseThrow();
                    for (ExamSection section : option.sections()) appendSection(section, option.id(), blocks);
                    if (!option.blocks().isEmpty()) {
                        SectionOnPath synthetic = sections.get(option.id() + ":blocks");
                        if (synthetic != null) {
                            ExamSection section = new ExamSection(synthetic.sectionId(), synthetic.subjectCode(),
                                    synthetic.questionCount(), null, null, null, null, option.blocks());
                            appendSection(section, option.id(), blocks);
                        }
                    }
                }
                partMinutes += part.durationMinutes() == null ? 0 : part.durationMinutes();
                parts.add(new PreviewPart(part.id(), part.name(), part.order(), part.durationMinutes(),
                        first, number - 1, totalPoints.subtract(before), part.maxPoints(), blocks));
            }
            boolean shared = "SHARED".equals(exam.timing());
            int minutes = shared && exam.durationMinutes() != null ? exam.durationMinutes() : partMinutes;
            String pointsStatus = !"RAW".equals(exam.practiceScoring()) || exam.maxScore() == null
                    ? "NOT_APPLICABLE" : totalPoints.compareTo(exam.maxScore()) == 0 ? "PASS" : "FAIL";
            List<PreviewCheck> checks = List.of(new PreviewCheck("NO_DUPLICATES", "PASS", BigDecimal.ZERO),
                    new PreviewCheck("SETS_INTACT", hasSet ? "PASS" : "NOT_APPLICABLE", null),
                    new PreviewCheck("TOTAL_POINTS", pointsStatus, totalPoints));
            List<KnowledgeStat> stats = knowledgeStats.values().stream()
                    .sorted(Comparator.comparingInt(KnowledgeStat::count).reversed()
                            .thenComparing(KnowledgeStat::code)).toList();
            return new PreviewPaper(number - 1, totalPoints, minutes, shared, manual, parts, checks, stats, subjects);
        }

        private void appendSection(ExamSection section, String optionId, List<PreviewBlock> out) {
            SectionOnPath resolved = sections.get(section.id());
            if (resolved == null) return;
            List<QuestionSourcePort.Unit> sectionUnits = new ArrayList<>();
            for (QuestionBlock block : section.blocks()) {
                GroupWitness group = witnesses.get(block.id());
                if (group == null) throw new IllegalStateException("Missing group witness " + block.id());
                List<QuestionSourcePort.Unit> units = new ArrayList<>();
                List<PreviewItem> items = new ArrayList<>();
                int first = number;
                boolean blockManual = false;
                for (QuestionPick pick : group.picks()) {
                    var version = versions.get(key(pick));
                    var candidate = candidates.get(key(pick));
                    if (version == null || candidate == null) throw new IllegalStateException("Missing selected question");
                    units.addAll(candidate.units());
                    blockManual |= candidate.units().stream().anyMatch(u -> u.answerType().name().equals("ESSAY"));
                    items.add(item(version, candidate, section.subjectCode()));
                }
                sectionUnits.addAll(units);
                var requirement = requirement(block.matrix(), units);
                var allocation = allocation(requirement, units);
                out.add(new PreviewBlock(block.id(), block.name(), section.subjectCode(),
                        block.questionType() == null ? null : block.questionType().name(), blockManual,
                        block.requiredPointsPerQuestion() == null ? BigDecimal.ONE : block.requiredPointsPerQuestion(),
                        first, number - 1, units.size(), block.setCount(), block.setSize(), requirement, allocation, items));
                manual |= blockManual;
            }
            subjects.add(subject(section, resolved.questionCount(), optionId, sectionUnits));
        }

        private PreviewItem item(QuestionSourcePort.Version version, QuestionSourcePort.Candidate candidate,
                String subjectCode) {
            JsonNode snapshot = version.snapshot();
            JsonNode body = snapshot.path("body");
            if ("QUESTION_SET".equals(text(body, "type"))) {
                hasSet = true;
                List<PreviewQuestion> children = new ArrayList<>();
                for (var unit : candidate.units()) {
                    if (unit.questionId() == null) throw new IllegalStateException("Set child ID missing");
                    var childVersion = versions.get(new QuestionSourcePort.VersionKey(
                            unit.questionId(), version.key().versionNo()));
                    if (childVersion == null) throw new IllegalStateException("Set child version missing");
                    String code = version.childCodes().get(unit.questionId());
                    if (code == null) throw new IllegalStateException("Set child code missing");
                    children.add(question(unit.questionId().toString(), code, version.key().versionNo(),
                            childVersion.snapshot(), unit, subjectCode));
                }
                return new PreviewItem("SET", null, version.key().questionId().toString(), version.code(),
                        version.key().versionNo(), false, text(body, "passage"), children, null);
            }
            if (candidate.units().isEmpty()) throw new IllegalStateException("Question has no counting units");
            int start = number;
            PreviewQuestion question = question(version.key().questionId().toString(), version.code(),
                    version.key().versionNo(), snapshot, candidate.units().getFirst(), subjectCode);
            // A cloze is displayed once but every blank contributes to its numbering and points.
            for (int i = 1; i < candidate.units().size(); i++) {
                number++;
                totalPoints = totalPoints.add(points(candidate.units().get(i)));
                countKnowledge(snapshot, subjectCode);
            }
            if (number <= start) throw new IllegalStateException("Invalid question numbering");
            return new PreviewItem("QUESTION", question, null, null, null, false, null, null, null);
        }

        private PreviewQuestion question(String id, String code, int version, JsonNode snapshot,
                QuestionSourcePort.Unit unit, String subjectCode) {
            BigDecimal points = points(unit);
            number++;
            totalPoints = totalPoints.add(points);
            KnowledgeRef ref = countKnowledge(snapshot, subjectCode);
            JsonNode body = snapshot.path("body");
            if (body instanceof ObjectNode object && "MATCHING".equals(text(body, "type"))
                    && (body.has("shuffleLeft") || body.has("shuffleRight"))) {
                ObjectNode cleaned = object.deepCopy();
                cleaned.remove(List.of("shuffleLeft", "shuffleRight"));
                body = cleaned;
            }
            if (body instanceof ObjectNode object
                    && ("SINGLE_CHOICE".equals(text(body, "type")) || "MULTIPLE_CHOICE".equals(text(body, "type")))
                    && !body.hasNonNull("shuffleOptions")) {
                ObjectNode normalized = object.deepCopy();
                normalized.put("shuffleOptions", true);
                body = normalized;
            }
            return new PreviewQuestion(number - 1, id, code, version, unit.answerType().name(), false,
                    text(snapshot, "content"), unit.gradeCode(), unit.difficultyCode(), ref, points,
                    options(body), answer(body), text(snapshot, "solution"), rubric(body), body.isMissingNode() ? null : body,
                    null, null, null);
        }

        private static BigDecimal points(QuestionSourcePort.Unit unit) {
            if (unit.points() == null) throw new IllegalStateException("Selected unit has no points");
            return unit.points();
        }

        private KnowledgeRef countKnowledge(JsonNode snapshot, String subjectCode) {
            JsonNode refs = snapshot.path("knowledge");
            JsonNode selected = null;
            for (JsonNode ref : refs) {
                if (selected == null || ref.path("primary").asBoolean(false)) selected = ref;
                if (ref.path("primary").asBoolean(false)) break;
            }
            if (selected == null || !selected.hasNonNull("itemId")) return null;
            UUID id = UUID.fromString(selected.get("itemId").asText());
            CatalogItemView node = knowledge.get(id);
            if (node == null) return null;
            String[] path = node.path().split("/");
            UUID chapterId = path.length > 1 ? UUID.fromString(path[1]) : id;
            CatalogItemView chapter = knowledge.getOrDefault(chapterId, node);
            KnowledgeStat previous = knowledgeStats.get(chapterId);
            knowledgeStats.put(chapterId, new KnowledgeStat(chapterId.toString(), chapter.code(),
                    name(chapter), subjectCode, previous == null ? 1 : previous.count() + 1));
            return new KnowledgeRef(id.toString(), node.code(), name(node));
        }

        private static String name(CatalogItemView item) {
            return "en".equalsIgnoreCase(LocaleConfig.currentLocale().getLanguage())
                    ? item.nameEn() : item.nameVi();
        }

        private static List<PreviewOption> options(JsonNode body) {
            JsonNode entries = body.path("options");
            boolean statements = false;
            if (!entries.isArray()) { entries = body.path("statements"); statements = true; }
            if (!entries.isArray()) return null;
            List<PreviewOption> out = new ArrayList<>();
            for (JsonNode item : entries) out.add(new PreviewOption(text(item, "id"), text(item, "content"),
                    item.path(statements ? "answer" : "correct").asBoolean(false)));
            return out;
        }

        private static String answer(JsonNode body) {
            JsonNode answers = body.path("answers");
            if (!answers.isArray()) return null;
            List<String> out = new ArrayList<>();
            for (JsonNode value : answers) out.add(value.asText());
            return out.isEmpty() ? null : String.join(" / ", out);
        }

        private static List<RubricItem> rubric(JsonNode body) {
            JsonNode items = body.path("rubric");
            if (!items.isArray()) return null;
            List<RubricItem> out = new ArrayList<>();
            for (JsonNode item : items) out.add(new RubricItem(text(item, "id"), text(item, "description"),
                    item.path("points").isNumber() ? item.path("points").decimalValue() : null));
            return out;
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }

        private static BlockRequirement requirement(BlockMatrix matrix, List<QuestionSourcePort.Unit> units) {
            if (matrix == null) {
                Map<List<String>, Integer> cells = counts(units, u -> java.util.Arrays.asList(u.gradeCode(), u.difficultyCode()));
                return new BlockRequirement("COMBINED", cells.entrySet().stream()
                        .map(e -> new RequirementCell(e.getKey().get(0), e.getKey().get(1), e.getValue())).toList(),
                        List.of(), List.of(), List.of());
            }
            List<Integer> cellValues = resolve(matrix.cells().stream().map(MatrixCell::value).toList(), matrix.unit(), units.size());
            List<RequirementCell> cells = new ArrayList<>();
            for (int i = 0; i < matrix.cells().size(); i++) {
                var c = matrix.cells().get(i);
                cells.add(new RequirementCell(c.gradeCode(), c.difficultyCode(), cellValues.get(i)));
            }
            return new BlockRequirement(matrix.mode().name(), cells,
                    dimensions(matrix.byGrade(), matrix.unit(), units.size()),
                    dimensions(matrix.byDifficulty(), matrix.unit(), units.size()),
                    dimensions(matrix.byCompetency(), matrix.unit(), units.size()));
        }

        private static List<AllocationCell> allocation(BlockRequirement requirement, List<QuestionSourcePort.Unit> units) {
            Map<List<String>, Integer> generated = counts(units, u -> java.util.Arrays.asList(u.gradeCode(), u.difficultyCode()));
            if ("COMBINED".equals(requirement.mode())) {
                return requirement.cells().stream().map(c -> new AllocationCell(c.gradeCode(), c.difficultyCode(), c.required(),
                        generated.getOrDefault(java.util.Arrays.asList(c.gradeCode(), c.difficultyCode()), 0))).toList();
            }
            return generated.entrySet().stream().map(e -> new AllocationCell(e.getKey().get(0), e.getKey().get(1), null, e.getValue())).toList();
        }

        private static SubjectAllocation subject(ExamSection section, Integer count, String optionId,
                List<QuestionSourcePort.Unit> units) {
            List<SubjectQuotaRow> answers = new ArrayList<>();
            if (section.answerQuotas() != null) {
                var quotas = section.answerQuotas();
                List<Integer> values = resolve(quotas.items().stream().map(AnswerQuota::value).toList(), quotas.unit(), units.size());
                for (int i = 0; i < quotas.items().size(); i++) {
                    String code = quotas.items().get(i).answerType().name();
                    answers.add(new SubjectQuotaRow(code, values.get(i),
                            (int) units.stream().filter(u -> u.answerType().name().equals(code)).count()));
                }
            }
            List<SubjectDimensionRow> grades = new ArrayList<>();
            List<SubjectDimensionRow> difficulties = new ArrayList<>();
            List<AllocationCell> cells = List.of();
            if (section.matrix() != null) {
                BlockRequirement req = requirement(section.matrix(), units);
                for (var dim : req.byGrade()) grades.add(new SubjectDimensionRow(dim.code(), dim.required(),
                        (int) units.stream().filter(u -> dim.code().equals(u.gradeCode())).count()));
                for (var dim : req.byDifficulty()) difficulties.add(new SubjectDimensionRow(dim.code(), dim.required(),
                        (int) units.stream().filter(u -> dim.code().equals(u.difficultyCode())).count()));
                cells = allocation(req, units);
            }
            return new SubjectAllocation(section.id(), section.subjectCode(), optionId, count, answers, grades, difficulties, cells);
        }

        private static List<RequirementDimension> dimensions(List<MatrixDimensionValue> values, MatrixUnit unit, int total) {
            List<Integer> resolved = resolve(values.stream().map(MatrixDimensionValue::value).toList(), unit, total);
            List<RequirementDimension> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) result.add(new RequirementDimension(values.get(i).code(), resolved.get(i)));
            return result;
        }

        private static List<Integer> resolve(List<BigDecimal> values, MatrixUnit unit, int total) {
            if (values.isEmpty()) return List.of();
            List<Integer> ints = values.stream().map(BigDecimal::intValueExact).toList();
            if (unit != MatrixUnit.PERCENT) return ints;
            int[] counts = new LargestRemainderApportioner().apportion(ints, total);
            List<Integer> result = new ArrayList<>(counts.length);
            for (int count : counts) result.add(count);
            return result;
        }

        private static <K> Map<K, Integer> counts(List<QuestionSourcePort.Unit> units,
                java.util.function.Function<QuestionSourcePort.Unit, K> key) {
            Map<K, Integer> result = new LinkedHashMap<>();
            for (var unit : units) result.merge(key.apply(unit), 1, Integer::sum);
            return result;
        }
    }
}
