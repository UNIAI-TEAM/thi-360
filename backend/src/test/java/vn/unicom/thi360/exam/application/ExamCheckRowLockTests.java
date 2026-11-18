package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** BE-20 rows 1 to 4 and 8 to 9: row locks (H42), omitted groups (H43) and the scale rule (H49) on fake templates. */
class ExamCheckRowLockTests {
    private static final UUID KIND = UUID.randomUUID();
    private static final UUID SUBJECT_ITEM = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final ExamTemplateService templates = mock(ExamTemplateService.class);
    private final Map<String, UUID> subjects = new HashMap<>();
    private final Map<UUID, List<Candidate>> candidates = new HashMap<>();
    private final SolverLimits limits = SolverLimits.defaults();

    // ---- rows 1 and 2: BLOCK_COUNT_LOCKED ------------------------------------------------------------------

    /** Part 1 of an H-SCA-like template: 20 single choice and 5 multiple choice, then a 3 x 4 set row. */
    private static final String ROWS = """
            {"parts":[{"id":"p1","name":"Part 1","questionCount":37,"sections":[
              {"id":"s1","subjectCode":"TOAN","questionCount":37,"blocks":[
                {"id":"b-single","name":"Single","templateGroupCode":"G1","questionType":"SINGLE_CHOICE","questionCount":20},
                {"id":"b-multi","name":"Multi","templateGroupCode":"G2","questionType":"MULTIPLE_CHOICE","questionCount":5},
                {"id":"b-set","name":"Set","templateGroupCode":"G3","questionType":"QUESTION_SET",
                 "childAnswerType":"SINGLE_CHOICE","setCount":3,"setSize":4}]}]}]}
            """;

    private ExamCheckRules rules(String exam, String blockLocked, String omittedLocked) throws Exception {
        return new ExamCheckRules(mapper, exam(1), document(exam), Map.of(), Map.of(),
                new ExamCheckRules.TemplateFacts(null, null, blockLocked == null ? null : document(blockLocked),
                        omittedLocked == null ? null : document(omittedLocked)));
    }

    @Test
    void changingPart1To22And3IsLockedAtEachRowWithTheTemplateAndCurrentNumbers() throws Exception {
        String edited = ROWS.replace("\"questionCount\":20", "\"questionCount\":22")
                .replace("\"MULTIPLE_CHOICE\",\"questionCount\":5", "\"MULTIPLE_CHOICE\",\"questionCount\":3");
        List<ObjectNode> locked = of(rules(edited, ROWS, null).common(), "BLOCK_COUNT_LOCKED");
        assertThat(locked).hasSize(2);
        ObjectNode single = at(locked, "b-single");
        assertThat(single.path("severity").asText()).isEqualTo("ERROR");
        assertThat(single.path("group").asText()).isEqualTo("STRUCTURE");
        assertThat(single.path("partId").asText()).isEqualTo("p1");
        assertThat(single.path("sectionId").asText()).isEqualTo("s1");
        assertThat(single.path("blockName").asText()).isEqualTo("Single");
        assertThat(single.path("dimension").asText()).isEqualTo("COUNT");
        assertThat(single.path("required").asInt()).isEqualTo(20);
        assertThat(single.path("available").asInt()).isEqualTo(22);
        assertThat(single.path("location").path("blockId").asText()).isEqualTo("b-single");
        assertThat(at(locked, "b-multi").path("required").asInt()).isEqualTo(5);
        assertThat(at(locked, "b-multi").path("available").asInt()).isEqualTo(3);
    }

    @Test
    void changingTheSetCountSetSizeOrTypeOfARowIsLockedAndTheDimensionNamesWhatChanged() throws Exception {
        ObjectNode sets = at(of(rules(ROWS.replace("\"setCount\":3", "\"setCount\":4"), ROWS, null).common(),
                "BLOCK_COUNT_LOCKED"), "b-set");
        assertThat(sets.path("dimension").asText()).isEqualTo("SET_COUNT");
        assertThat(sets.path("required").asInt()).isEqualTo(3);
        assertThat(sets.path("available").asInt()).isEqualTo(4);
        ObjectNode size = at(of(rules(ROWS.replace("\"setSize\":4", "\"setSize\":5"), ROWS, null).common(),
                "BLOCK_COUNT_LOCKED"), "b-set");
        assertThat(size.path("dimension").asText()).isEqualTo("SET_SIZE");
        ObjectNode type = at(of(rules(ROWS.replace("\"SINGLE_CHOICE\",\"questionCount\":20",
                "\"MULTIPLE_CHOICE\",\"questionCount\":20"), ROWS, null).common(), "BLOCK_COUNT_LOCKED"), "b-single");
        assertThat(type.path("dimension").asText()).isEqualTo("TYPE");
    }

    @Test
    void anUnchangedCopyWithRegeneratedIdsAndRenamedRowsHasNoRowLock() throws Exception {
        ExamStructureDocument copy = vn.unicom.thi360.exam.domain.ExamStructureOps.regenerateIds(document(
                ROWS.replace("\"name\":\"Single\"", "\"name\":\"Renamed\"")));
        var rules = new ExamCheckRules(mapper, exam(1), copy, Map.of(), Map.of(),
                new ExamCheckRules.TemplateFacts(null, null, document(ROWS), null));
        assertThat(codes(rules.common())).doesNotContain("BLOCK_COUNT_LOCKED");
    }

    @Test
    void aRowDeletedOrAddedIsLockedAtTheSection() throws Exception {
        ObjectNode tree = (ObjectNode) mapper.readTree(ROWS);
        ((com.fasterxml.jackson.databind.node.ArrayNode) tree.at("/parts/0/sections/0/blocks")).remove(1);
        List<ObjectNode> missing = of(rules(tree.toString(), ROWS, null).common(), "BLOCK_COUNT_LOCKED");
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().path("sectionId").asText()).isEqualTo("s1");
        assertThat(missing.getFirst().path("required").asInt()).isEqualTo(5);
        assertThat(missing.getFirst().path("available").asInt()).isZero();
        ((com.fasterxml.jackson.databind.node.ArrayNode) tree.at("/parts/0/sections/0/blocks")).addObject()
                .put("id", "b-extra").put("name", "Extra").put("templateGroupCode", "G9")
                .put("questionType", "SHORT_ANSWER").put("questionCount", 2);
        List<ObjectNode> both = of(rules(tree.toString(), ROWS, null).common(), "BLOCK_COUNT_LOCKED");
        assertThat(at(both, "b-extra").path("required").asInt()).isZero();
        assertThat(at(both, "b-extra").path("available").asInt()).isEqualTo(2);
    }

    @Test
    void onlyASubjectExamReceivesTheRowLockFromItsTemplate() {
        ExamTemplate template = mock(ExamTemplate.class);
        when(template.getCode()).thenReturn("T");
        when(template.isFixedBlockCounts()).thenReturn(true);
        when(template.getStructure()).thenReturn(readTree(ROWS));
        when(templates.findProvenance("T")).thenReturn(template);
        var facts = new ExamCheckTemplateFacts(catalog, templates, mapper);
        assertThat(facts.of(scopedExam("SUBJECT", "T")).blockLockedTemplate()).isNotNull();
        assertThat(facts.of(scopedExam("PART", "T")).blockLockedTemplate()).isNull();
    }

    // ---- row 3: V-ACT keeps H27 ----------------------------------------------------------------------------

    @Test
    void movingQuestionsBetweenGroupsOfASectionUnderAFixedCountTemplateKeepsH27() throws Exception {
        String moved = ROWS.replace("\"questionCount\":20", "\"questionCount\":18")
                .replace("\"MULTIPLE_CHOICE\",\"questionCount\":5", "\"MULTIPLE_CHOICE\",\"questionCount\":7");
        var rules = new ExamCheckRules(mapper, exam(1), document(moved), Map.of(), Map.of(),
                new ExamCheckRules.TemplateFacts(null, document(ROWS)));
        assertThat(codes(rules.common())).doesNotContain("STRUCTURE_COUNT_LOCKED", "BLOCK_COUNT_LOCKED");
    }

    // ---- row 4: OMITTED_BLOCK_LOCKED -----------------------------------------------------------------------

    private static final String WRITING = """
            {"parts":[{"id":"p1","name":"Reading","questionCount":20,"sections":[
              {"id":"s1","subjectCode":"VAN","questionCount":20,"blocks":[
                {"id":"b-read","name":"Read","templateGroupCode":"R","questionType":"SINGLE_CHOICE","questionCount":20}]}]},
             {"id":"p2","name":"Writing","questionCount":0,"sections":[
              {"id":"s2","subjectCode":"VAN","questionCount":0,"blocks":[
                {"id":"b-write","name":"Write","templateGroupCode":"W","questionType":"ESSAY","questionCount":1,
                 "requiredPointsPerQuestion":30,"omitted":true}]}]}]}
            """;

    @Test
    void turningAnOmittedGroupBackOnIsLockedOnlyForATemplateWithTheFlag() throws Exception {
        String reenabled = WRITING.replace("\"omitted\":true", "\"omitted\":false");
        List<ObjectNode> locked = of(rules(reenabled, WRITING, WRITING).common(), "OMITTED_BLOCK_LOCKED");
        assertThat(locked).hasSize(1);
        ObjectNode issue = locked.getFirst();
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("partId").asText()).isEqualTo("p2");
        assertThat(issue.path("sectionId").asText()).isEqualTo("s2");
        assertThat(issue.path("blockId").asText()).isEqualTo("b-write");
        assertThat(issue.path("location").path("blockId").asText()).isEqualTo("b-write");
        assertThat(codes(rules(reenabled, WRITING, WRITING).common())).doesNotContain("BLOCK_COUNT_LOCKED");
        assertThat(codes(rules(reenabled, null, null).common())).doesNotContain("OMITTED_BLOCK_LOCKED");
        assertThat(codes(rules(WRITING, WRITING, WRITING).common())).doesNotContain("OMITTED_BLOCK_LOCKED",
                "BLOCK_COUNT_LOCKED");
    }

    // ---- rows 8 and 9: SCALE_INCLUDES_EXCLUDED_PART --------------------------------------------------------

    /** 20 questions of 6 points, and a writing part of one omitted group of 30 points: the paper holds 120. */
    private static final String VSAT_VAN = """
            {"parts":[{"id":"p1","name":"Reading","questionCount":20,"sections":[
              {"id":"s1","subjectCode":"VAN","questionCount":20,"blocks":[
                {"id":"b-read","name":"Read","questionType":"SINGLE_CHOICE","questionCount":20,"requiredPointsPerQuestion":6}]}]},
             {"id":"p4","name":"Viết","questionCount":0,"sections":[
              {"id":"s4","subjectCode":"VAN","questionCount":0,"blocks":[
                {"id":"b-write","name":"Write","questionType":"ESSAY","questionCount":1,
                 "requiredPointsPerQuestion":30,"omitted":true}]}]}]}
            """;

    @Test
    void aScaleThatCountsTheOmittedPartIsNamedAndReplacesThePointsMismatch() throws Exception {
        JsonNode result = checkScale(VSAT_VAN, true, "150");
        JsonNode issue = issue(result, "SCALE_INCLUDES_EXCLUDED_PART");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("group").asText()).isEqualTo("POINTS");
        assertThat(issue.path("partId").asText()).isEqualTo("p4");
        assertThat(issue.path("partName").asText()).isEqualTo("Viết");
        assertThat(issue.path("required").decimalValue()).isEqualByComparingTo("150");
        assertThat(issue.path("available").decimalValue()).isEqualByComparingTo("120");
        assertThat(issueCodes(result)).doesNotContain("POINTS_TOTAL_MISMATCH", "PART_EMPTY", "PART_TOTAL_MISMATCH",
                "PART_POINTS_MISMATCH");
    }

    @Test
    void anyOtherGapKeepsThePointsMismatchAsBefore() throws Exception {
        JsonNode other = checkScale(VSAT_VAN, true, "140");
        assertThat(issueCodes(other)).contains("POINTS_TOTAL_MISMATCH").doesNotContain("SCALE_INCLUDES_EXCLUDED_PART");
        assertThat(issue(other, "POINTS_TOTAL_MISMATCH").path("available").decimalValue()).isEqualByComparingTo("120");
        // SP2E-like template: omitted writing of 4 points, scale 10 over a paper of 6, template without the flag.
        String sp2e = VSAT_VAN.replace("\"requiredPointsPerQuestion\":6", "\"requiredPointsPerQuestion\":0.3")
                .replace("\"requiredPointsPerQuestion\":30", "\"requiredPointsPerQuestion\":4");
        JsonNode result = checkScale(sp2e, false, "10");
        assertThat(issueCodes(result)).contains("POINTS_TOTAL_MISMATCH").doesNotContain("SCALE_INCLUDES_EXCLUDED_PART");
    }

    private JsonNode checkScale(String structure, boolean omittedLocked, String scale) throws Exception {
        ExamTemplate template = mock(ExamTemplate.class);
        when(template.getCode()).thenReturn("T");
        when(template.isOmittedLocked()).thenReturn(omittedLocked);
        when(template.getStructure()).thenReturn(mapper.readTree(structure));
        when(templates.findProvenance("T")).thenReturn(template);
        UUID subjectId = UUID.randomUUID();
        subjects.put("VAN", subjectId);
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            pool.add(new Candidate(id, 1, null, List.of(new Unit(id, null, QuestionType.SINGLE_CHOICE, "G", "D",
                    List.of(), List.of(), BigDecimal.ONE)), null, null));
        }
        candidates.put(subjectId, pool);
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", KIND, "FULL", "SHARED", 60, new BigDecimal(scale),
                "RAW", clock.instant(), UUID.randomUUID());
        exam.applyTemplate("T", null, null, "SHARED", 60, null, UUID.randomUUID(), clock.instant());
        exam.applyStructureSummary(20, false, false, 0);
        return check(exam, document(structure)).result();
    }

    // ---- harness -------------------------------------------------------------------------------------------

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document) {
        CatalogItemView kind = named("HSCA");
        CatalogItemView subject = named("VAN");
        when(catalog.findItem(KIND)).thenReturn(Optional.of(kind));
        when(catalog.findItem(SUBJECT_ITEM)).thenReturn(Optional.of(subject));
        when(catalog.findActiveItem(eq("SUBJECT"), any())).thenAnswer(invocation -> {
            UUID id = subjects.get(invocation.getArgument(1));
            if (id == null) return Optional.empty();
            CatalogItemView item = mock(CatalogItemView.class);
            when(item.id()).thenReturn(id);
            return Optional.of(item);
        });
        doAnswer(invocation -> candidates.getOrDefault(
                ((QuestionSourcePort.Filter) invocation.getArgument(0)).subjectId(), List.of()))
                .when(source).findCandidates(any(), anyInt());
        var unitStore = mock(ExamCheckUnitStore.class);
        when(unitStore.load(exam.getId())).thenReturn(Map.of());
        var unitEngine = new ExamCheckUnitEngine(unitStore,
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)), catalog, mapper, limits, clock);
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper,
                catalog, source, mock(QuestionAttachmentPort.class), unitEngine,
                new ExamCheckTemplateFacts(catalog, templates, mapper));
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, catalog.revision(), "hash",
                mapper.createObjectNode());
        return checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30), ExamCheckScope.from(null, null, null));
    }

    private static CatalogItemView named(String code) {
        CatalogItemView view = mock(CatalogItemView.class);
        when(view.code()).thenReturn(code);
        return view;
    }

    private Exam scopedExam(String scope, String templateCode) {
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", KIND, "FULL", "SHARED", 60, null, "RAW",
                clock.instant(), UUID.randomUUID());
        exam.changeScope(KIND, scope, "PART".equals(scope) ? "P3" : null, SUBJECT_ITEM, null, null, true, null,
                "SHARED", 60, null, UUID.randomUUID(), clock.instant());
        exam.applyTemplate(templateCode, null, null, "SHARED", 60, null, UUID.randomUUID(), clock.instant());
        return exam;
    }

    private Exam exam(int count) {
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", KIND, "FULL", "SHARED", 60, null, "RAW",
                clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(count, false, false, 0);
        return exam;
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ExamStructureDocument document(String json) throws Exception {
        return mapper.readValue(json, ExamStructureDocument.class);
    }

    private static ObjectNode at(List<ObjectNode> issues, String blockId) {
        return issues.stream().filter(issue -> blockId.equals(issue.path("blockId").asText(null))).findFirst()
                .orElseThrow(() -> new AssertionError("no issue at " + blockId + " in " + issues));
    }

    private static List<ObjectNode> of(List<ObjectNode> issues, String code) {
        return issues.stream().filter(issue -> code.equals(issue.path("code").asText())).toList();
    }

    private static List<String> codes(List<ObjectNode> issues) {
        return issues.stream().map(issue -> issue.path("code").asText()).toList();
    }

    private static List<String> issueCodes(JsonNode result) {
        return result.path("groups").findValuesAsText("code");
    }

    private static JsonNode issue(JsonNode result, String code) {
        return StreamSupport.stream(result.path("groups").findValues("issues").spliterator(), false)
                .flatMap(list -> StreamSupport.stream(list.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " missing in " + issueCodes(result)));
    }
}
