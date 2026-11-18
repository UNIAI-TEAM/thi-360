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
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** BE-15 rules on fixtures that set the template flags directly (publishable, fixed_counts). */
class ExamCheckTemplateRulesTests {
    private static final UUID KIND = UUID.randomUUID();
    private static final UUID SUBJECT_ITEM = UUID.randomUUID();
    private static final UUID NODE_A = UUID.randomUUID();
    private static final UUID NODE_B = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final ExamTemplateService templates = mock(ExamTemplateService.class);
    private final Map<String, UUID> subjects = new HashMap<>();
    private final Map<UUID, List<Candidate>> candidates = new HashMap<>();
    private final SolverLimits limits = SolverLimits.defaults();

    // ---- rows 1, 2: TEMPLATE_NOT_PUBLISHABLE -------------------------------------------------------------

    @Test
    void subjectExamOfAnUnpublishableStandardTemplateFailsTheCheck() throws Exception {
        Exam exam = subjectExam("SUBJECT", "STANDARD");
        standardTemplate(false);
        subject("VAN", 1);
        ExamChecker.Evaluation evaluation = check(exam, document(oneBlock("VAN", 1)));
        JsonNode issue = issue(evaluation.result(), "TEMPLATE_NOT_PUBLISHABLE");
        assertThat(evaluation.result().path("status").asText()).isEqualTo("INVALID");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("group").asText()).isEqualTo("STRUCTURE");
        assertThat(issue.path("subjectCode").asText()).isEqualTo("VAN");
        assertThat(issue.path("location").isNull()).isTrue();
    }

    @Test
    void reviewAndPartExamsOfTheSameSubjectAreAlsoNotPublishable() throws Exception {
        standardTemplate(false);
        subject("VAN", 1);
        for (String[] shape : new String[][] {{"SUBJECT", "REVIEW"}, {"PART", "STANDARD"}}) {
            Exam exam = subjectExam(shape[0], shape[1]);
            JsonNode result = check(exam, document(oneBlock("VAN", 1))).result();
            assertThat(issueCodes(result)).as(shape[0] + "/" + shape[1]).contains("TEMPLATE_NOT_PUBLISHABLE");
            assertThat(result.path("status").asText()).isEqualTo("INVALID");
        }
    }

    @Test
    void publishableStandardTemplateDoesNotRaiseTheCode() throws Exception {
        Exam exam = subjectExam("SUBJECT", "STANDARD");
        standardTemplate(true);
        subject("VAN", 1);
        JsonNode result = check(exam, document(oneBlock("VAN", 1))).result();
        assertThat(issueCodes(result)).doesNotContain("TEMPLATE_NOT_PUBLISHABLE");
        assertThat(result.path("status").asText()).isEqualTo("VALID");
    }

    @Test
    void templateFlagsAreReadFromTheExamsOwnSubjectOnlyAndTheLoadedRowIsReused() throws Exception {
        Exam exam = subjectExam("SUBJECT", "STANDARD");
        exam.applyTemplate("VAN_STANDARD", null, null, null, null, null, UUID.randomUUID(), clock.instant());
        ExamTemplate standard = template(false, true, "{\"parts\":[]}");
        when(standard.getCode()).thenReturn("VAN_STANDARD");
        when(templates.findBestMatch("THPTQG", "SUBJECT", null, "VAN", null)).thenReturn(standard);
        subject("VAN", 1);
        JsonNode result = check(exam, document(oneBlock("VAN", 1))).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        org.mockito.Mockito.verify(templates, org.mockito.Mockito.times(1))
                .findBestMatch(any(), any(), any(), any(), any());
        org.mockito.Mockito.verify(templates, org.mockito.Mockito.never()).findProvenance(any());
        org.mockito.Mockito.verify(templates, org.mockito.Mockito.never()).findDefault(any(), any(), any(), any(), any());
        org.mockito.Mockito.verify(templates, org.mockito.Mockito.never()).findActive(any());
    }

    // ---- rows 4, 5, 6: STRUCTURE_COUNT_LOCKED ------------------------------------------------------------

    private static final String LOCKED_TEMPLATE = """
            {"parts":[{"id":"p","name":"Thinking","questionCount":30,"sections":[
              {"id":"s-logic","subjectCode":"SCIENTIFIC_THINKING","questionCount":12,
               "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE","difficultyEnforcement":"REFERENCE",
                         "byDifficulty":[{"code":"NB","value":1},{"code":"TH","value":4},{"code":"VD","value":7}]},
               "blocks":[{"id":"b-logic","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE","setCount":4,"setSize":3}]},
              {"id":"s-reason","subjectCode":"SCIENTIFIC_THINKING","questionCount":18,
               "blocks":[{"id":"b-reason","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE","setCount":6,"setSize":3}]}
            ]}]}
            """;

    private static String lockedBase(String logicCount, String logicBlock, String difficulty) {
        return LOCKED_TEMPLATE
                .replace("\"questionCount\":12,", "\"questionCount\":" + logicCount + ",")
                .replace("\"setCount\":4,\"setSize\":3", logicBlock)
                .replace("{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}", difficulty);
    }

    @Test
    void lowerLogicCountOfAFixedCountExamIsLockedAtTheLogicSection() throws Exception {
        for (String block : List.of("\"questionCount\":10", "\"setCount\":4,\"setSize\":3")) {
            // First: the section total and its group change together; second: only the declared total changes.
            var rules = rulesWith(lockedBase("10", block, "{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}"),
                    LOCKED_TEMPLATE);
            List<ObjectNode> locked = of(rules.common(), "STRUCTURE_COUNT_LOCKED");
            assertThat(locked).as(block).hasSize(1);
            ObjectNode issue = locked.getFirst();
            assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
            assertThat(issue.path("group").asText()).isEqualTo("STRUCTURE");
            assertThat(issue.path("partId").asText()).isEqualTo("p");
            assertThat(issue.path("sectionId").asText()).isEqualTo("s-logic");
            assertThat(issue.path("subjectCode").asText()).isEqualTo("SCIENTIFIC_THINKING");
            assertThat(issue.path("required").asInt()).isEqualTo(12);
            assertThat(issue.path("available").asInt()).isEqualTo(10);
            assertThat(issue.path("location").path("sectionId").asText()).isEqualTo("s-logic");
            assertThat(issue.path("location").has("blockId")).isFalse();
        }
    }

    @Test
    void changingOnlyAGroupCountUnderTheSameDeclaredTotalIsAlsoLocked() throws Exception {
        var rules = rulesWith(lockedBase("12", "\"setCount\":3,\"setSize\":3",
                "{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}"), LOCKED_TEMPLATE);
        List<ObjectNode> locked = of(rules.common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().path("sectionId").asText()).isEqualTo("s-logic");
        assertThat(locked.getFirst().path("required").asInt()).isEqualTo(12);
        assertThat(locked.getFirst().path("available").asInt()).isEqualTo(9);
    }

    @Test
    void redistributingDifficultyInsideALockedSectionIsAllowed() throws Exception {
        var rules = rulesWith(lockedBase("12", "\"setCount\":4,\"setSize\":3",
                "{\"code\":\"NB\",\"value\":2},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":6}"), LOCKED_TEMPLATE);
        assertThat(codes(rules.common())).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    private static final String SAME_DIFFICULTY =
            "{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}";

    @Test
    void duplicateWithRegeneratedIdsIsLockedAtTheCopiedLogicSectionWhenItsCountIsEdited() throws Exception {
        ExamStructureDocument copy = ExamStructureOps.regenerateIds(
                document(lockedBase("9", "\"questionCount\":9", SAME_DIFFICULTY)));
        assertThat(copy.parts().getFirst().id()).isNotEqualTo("p");
        List<ObjectNode> locked = of(rulesWith(copy, LOCKED_TEMPLATE).common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().path("sectionId").asText())
                .isEqualTo(copy.parts().getFirst().sections().getFirst().id());
        assertThat(locked.getFirst().path("required").asInt()).isEqualTo(12);
        assertThat(locked.getFirst().path("available").asInt()).isEqualTo(9);
    }

    @Test
    void unchangedDuplicateWithRegeneratedIdsHasNoLock() throws Exception {
        ExamStructureDocument copy = ExamStructureOps.regenerateIds(document(LOCKED_TEMPLATE));
        assertThat(codes(rulesWith(copy, LOCKED_TEMPLATE).common())).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void sectionWithoutTemplateCounterpartAndExtraSectionAreLocked() throws Exception {
        JsonNode removedTree = mapper.readTree(LOCKED_TEMPLATE);
        ((ArrayNode) removedTree.at("/parts/0/sections")).remove(1);
        List<ObjectNode> missing = of(rulesWith(document(removedTree.toString()), LOCKED_TEMPLATE).common(),
                "STRUCTURE_COUNT_LOCKED");
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().path("partId").asText()).isEqualTo("p");
        assertThat(missing.getFirst().path("sectionId").isNull()).isTrue();
        assertThat(missing.getFirst().path("required").asInt()).isEqualTo(18);
        assertThat(missing.getFirst().path("available").asInt()).isZero();

        JsonNode extraTree = mapper.readTree(LOCKED_TEMPLATE);
        ((ArrayNode) extraTree.at("/parts/0/sections")).add(mapper.readTree(
                "{\"id\":\"s-extra\",\"subjectCode\":\"SCIENTIFIC_THINKING\",\"questionCount\":3,"
                + "\"blocks\":[{\"id\":\"b-x\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":3}]}"));
        List<ObjectNode> extra = of(rulesWith(document(extraTree.toString()), LOCKED_TEMPLATE).common(),
                "STRUCTURE_COUNT_LOCKED");
        assertThat(extra).hasSize(1);
        assertThat(extra.getFirst().path("sectionId").asText()).isEqualTo("s-extra");
        assertThat(extra.getFirst().path("required").asInt()).isZero();
        assertThat(extra.getFirst().path("available").asInt()).isEqualTo(3);
    }

    @Test
    void missingAndExtraPartsAreLocked() throws Exception {
        JsonNode noPart = mapper.readTree(LOCKED_TEMPLATE);
        ((ArrayNode) noPart.path("parts")).removeAll();
        List<ObjectNode> missing = of(rulesWith(document(noPart.toString()), LOCKED_TEMPLATE).common(),
                "STRUCTURE_COUNT_LOCKED");
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().path("location").isNull()).isTrue();
        assertThat(missing.getFirst().path("required").asInt()).isEqualTo(30);

        JsonNode twoParts = mapper.readTree(LOCKED_TEMPLATE);
        ((ArrayNode) twoParts.path("parts")).add(mapper.readTree(
                "{\"id\":\"p2\",\"name\":\"Extra\",\"questionCount\":5,\"sections\":[]}"));
        List<ObjectNode> extra = of(rulesWith(document(twoParts.toString()), LOCKED_TEMPLATE).common(),
                "STRUCTURE_COUNT_LOCKED");
        assertThat(extra).hasSize(1);
        assertThat(extra.getFirst().path("partId").asText()).isEqualTo("p2");
    }

    // ---- copied sections are matched by content, never by array position ---------------------------------

    private static final String SIGNED_TEMPLATE = """
            {"parts":[{"id":"p","name":"Thinking","questionCount":30,"sections":[
              {"id":"s-logic","subjectCode":"SCIENTIFIC_THINKING","questionCount":12,
               "knowledgeScope":{"competencyCodes":["VACT-TD-LOGIC"]},
               "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE","difficultyEnforcement":"REFERENCE",
                         "byDifficulty":[{"code":"NB","value":1},{"code":"TH","value":4},{"code":"VD","value":7}]},
               "blocks":[{"id":"b-logic","templateGroupCode":"VACT_LOGIC","questionType":"QUESTION_SET",
                          "childAnswerType":"SINGLE_CHOICE","setCount":4,"setSize":3}]},
              {"id":"s-reason","subjectCode":"SCIENTIFIC_THINKING","questionCount":18,
               "knowledgeScope":{"competencyCodes":["VACT-TD-SL"]},
               "blocks":[{"id":"b-reason","templateGroupCode":"VACT_SCIENCE","questionType":"QUESTION_SET",
                          "childAnswerType":"SINGLE_CHOICE","setCount":6,"setSize":3}]}
            ]}]}
            """;

    /** Duplicate of the signed template (every id regenerated), then edited like a PUT of the working copy. */
    private ExamStructureDocument duplicateAndEdit(java.util.function.Consumer<ObjectNode> edit) throws Exception {
        ObjectNode tree = mapper.valueToTree(ExamStructureOps.regenerateIds(document(SIGNED_TEMPLATE)));
        edit.accept(tree);
        return mapper.treeToValue(tree, ExamStructureDocument.class);
    }

    private static ObjectNode sectionOf(ObjectNode tree, String competency) {
        for (JsonNode section : tree.at("/parts/0/sections")) {
            if (competency.equals(section.at("/knowledgeScope/competencyCodes/0").asText())) return (ObjectNode) section;
        }
        throw new AssertionError(competency);
    }

    private static void resize(ObjectNode section, int count, int sets) {
        section.put("questionCount", count);
        ((ObjectNode) section.at("/blocks/0")).put("setCount", sets);
    }

    private static void swapOrder(ObjectNode tree) {
        ArrayNode sections = (ArrayNode) tree.at("/parts/0/sections");
        JsonNode first = sections.remove(0);
        sections.add(first);
    }

    private List<ObjectNode> signedLocks(ExamStructureDocument copy) throws Exception {
        return of(rulesWith(copy, SIGNED_TEMPLATE).common(), "STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void reorderedCopyWithSwappedCountsIsLockedAtTheCopiedLogicSection() throws Exception {
        String[] logicId = new String[1];
        ExamStructureDocument copy = duplicateAndEdit(tree -> {
            resize(sectionOf(tree, "VACT-TD-LOGIC"), 18, 6);
            resize(sectionOf(tree, "VACT-TD-SL"), 12, 4);
            logicId[0] = sectionOf(tree, "VACT-TD-LOGIC").path("id").asText();
            swapOrder(tree);
        });
        List<ObjectNode> locked = signedLocks(copy);
        assertThat(locked).isNotEmpty();
        ObjectNode atLogic = locked.stream().filter(issue -> logicId[0].equals(issue.path("sectionId").asText(null)))
                .findFirst().orElseThrow(() -> new AssertionError("no lock at the copied Logic section: " + locked));
        assertThat(atLogic.path("location").path("sectionId").asText()).isEqualTo(logicId[0]);
        assertThat(atLogic.path("required").asInt()).isEqualTo(12);
        assertThat(atLogic.path("available").asInt()).isEqualTo(18);
    }

    @Test
    void copiesThatKeepEveryCountHaveNoLockWhateverTheOrder() throws Exception {
        assertThat(signedLocks(duplicateAndEdit(tree -> { }))).isEmpty();
        assertThat(signedLocks(duplicateAndEdit(ExamCheckTemplateRulesTests::swapOrder))).isEmpty();
    }

    @Test
    void removingTheCompetencyFilterOfALockedSectionIsLocked() throws Exception {
        ExamStructureDocument copy = duplicateAndEdit(tree ->
                ((ObjectNode) sectionOf(tree, "VACT-TD-LOGIC")).remove("knowledgeScope"));
        assertThat(signedLocks(copy)).isNotEmpty();
    }

    @Test
    void changingTheDifficultySplitInsideACopiedLockedSectionIsAllowed() throws Exception {
        ExamStructureDocument copy = duplicateAndEdit(tree -> {
            ArrayNode values = (ArrayNode) sectionOf(tree, "VACT-TD-LOGIC").at("/matrix/byDifficulty");
            ((ObjectNode) values.get(0)).put("value", 2);
            ((ObjectNode) values.get(2)).put("value", 6);
            swapOrder(tree);
        });
        assertThat(signedLocks(copy)).isEmpty();
    }

    @Test
    void identicalSignaturesWithCountsThatCannotBeMatchedCloseTheCheck() throws Exception {
        ObjectNode template = (ObjectNode) mapper.readTree(SIGNED_TEMPLATE);
        for (JsonNode section : template.at("/parts/0/sections")) ((ObjectNode) section).remove("knowledgeScope");
        for (JsonNode section : template.at("/parts/0/sections")) ((ObjectNode) section.at("/blocks/0")).remove("templateGroupCode");
        ObjectNode tree = mapper.valueToTree(ExamStructureOps.regenerateIds(mapper.treeToValue(template, ExamStructureDocument.class)));
        resize((ObjectNode) tree.at("/parts/0/sections/0"), 15, 5);
        resize((ObjectNode) tree.at("/parts/0/sections/1"), 15, 5);
        List<ObjectNode> locked = of(new ExamCheckRules(mapper, exam(30), mapper.treeToValue(tree, ExamStructureDocument.class),
                Map.of(), Map.of(), new ExamCheckRules.TemplateFacts(null, mapper.treeToValue(template, ExamStructureDocument.class)))
                .common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(locked).isNotEmpty();
    }

    private static final String REMAINDER_TEMPLATE = """
            {"parts":[{"id":"p","name":"Vietnamese","questionCount":30,"sections":[
              {"id":"s-vn","subjectCode":"VIETNAMESE","questionCount":30,
               "blocks":[{"id":"b-vn","questionType":"SINGLE_CHOICE","questionCount":30}]}]}]}
            """;

    @Test
    void takingTheRemainderInsteadOfAnExplicitCountOfTheSameTotalIsNotACountChange() throws Exception {
        String remainder = REMAINDER_TEMPLATE.replace(",\"questionCount\":30}]}]}]}", "}]}]}]}");
        assertThat(remainder).doesNotContain("\"b-vn\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\"");
        assertThat(codes(rulesWith(remainder, REMAINDER_TEMPLATE).common())).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void realCountChangeIsStillLockedWithOrWithoutARemainderGroup() throws Exception {
        String remainderOf28 = REMAINDER_TEMPLATE
                .replace(",\"questionCount\":30}]}]}]}", "}]}]}]}")
                .replace("\"s-vn\",\"subjectCode\":\"VIETNAMESE\",\"questionCount\":30",
                        "\"s-vn\",\"subjectCode\":\"VIETNAMESE\",\"questionCount\":28");
        List<ObjectNode> declared = of(rulesWith(remainderOf28, REMAINDER_TEMPLATE).common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(declared).hasSize(1);
        assertThat(declared.getFirst().path("sectionId").asText()).isEqualTo("s-vn");
        assertThat(declared.getFirst().path("required").asInt()).isEqualTo(30);
        assertThat(declared.getFirst().path("available").asInt()).isEqualTo(28);

        String explicit28 = REMAINDER_TEMPLATE.replace("\"b-vn\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":30",
                "\"b-vn\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":28");
        List<ObjectNode> groups = of(rulesWith(explicit28, REMAINDER_TEMPLATE).common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().path("required").asInt()).isEqualTo(30);
        assertThat(groups.getFirst().path("available").asInt()).isEqualTo(28);
    }

    @Test
    void partCountIsReportedOnlyWhenNoSectionOfThePartExplainsIt() throws Exception {
        String edited = LOCKED_TEMPLATE.replace("\"questionCount\":30", "\"questionCount\":28");
        List<ObjectNode> locked = of(rulesWith(edited, LOCKED_TEMPLATE).common(), "STRUCTURE_COUNT_LOCKED");
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().path("partId").asText()).isEqualTo("p");
        assertThat(locked.getFirst().path("sectionId").isNull()).isTrue();
        assertThat(locked.getFirst().path("location").has("sectionId")).isFalse();
    }

    @Test
    void examWithoutFixedCountsMayChangeTheLogicCount() throws Exception {
        Exam exam = fullExam("VACT_PART_LOGIC");
        ExamTemplate loaded = template(false, true, LOCKED_TEMPLATE);
        when(templates.findProvenance("VACT_PART_LOGIC")).thenReturn(loaded);
        subject("SCIENTIFIC_THINKING", 0);
        JsonNode result = check(exam, document(lockedBase("10", "\"questionCount\":10",
                "{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}"))).result();
        assertThat(issueCodes(result)).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void fixedCountExamFailsTheCheckThroughTheChecker() throws Exception {
        Exam exam = fullExam("VACT_FULL");
        ExamTemplate loaded = template(true, true, LOCKED_TEMPLATE);
        when(templates.findProvenance("VACT_FULL")).thenReturn(loaded);
        subject("SCIENTIFIC_THINKING", 0);
        JsonNode result = check(exam, document(lockedBase("10", "\"questionCount\":10",
                "{\"code\":\"NB\",\"value\":1},{\"code\":\"TH\",\"value\":4},{\"code\":\"VD\",\"value\":7}"))).result();
        JsonNode issue = issue(result, "STRUCTURE_COUNT_LOCKED");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(issue.path("location").path("sectionId").asText()).isEqualTo("s-logic");
    }

    @Test
    void lockedSectionIsIncompleteInSectionStatesAndTheOtherSectionIsNot() throws Exception {
        Exam exam = fullExam("VACT_FULL");
        ExamTemplate loaded = template(true, true, LOCKED_TEMPLATE);
        when(templates.findProvenance("VACT_FULL")).thenReturn(loaded);
        subject("SCIENTIFIC_THINKING", 30);
        subject("OTHER", 30);
        // The second section has its own subject, hence its own unit: a lock must not spill into it.
        String ownSubject = "\"s-reason\",\"subjectCode\":\"OTHER\"";
        String reason = "\"s-reason\",\"subjectCode\":\"SCIENTIFIC_THINKING\"";
        ExamStructureDocument edited = document(lockedBase("10", "\"questionCount\":10", SAME_DIFFICULTY)
                .replace(reason, ownSubject));
        check(exam, edited);
        assertThat(sectionStates(exam, edited)).containsExactly("s-logic=INCOMPLETE", "s-reason=NEEDS_CHECK");
        ExamStructureDocument untouched = document(LOCKED_TEMPLATE.replace(reason, ownSubject));
        assertThat(sectionStates(exam, untouched)).containsExactly("s-logic=NEEDS_CHECK", "s-reason=NEEDS_CHECK");
    }

    @Test
    void sectionScopedCheckOfASiblingInTheSameUnitStillReportsTheCommonLock() throws Exception {
        Exam exam = fullExam("VACT_FULL");
        ExamTemplate loaded = template(true, true, LOCKED_TEMPLATE);
        when(templates.findProvenance("VACT_FULL")).thenReturn(loaded);
        subject("SCIENTIFIC_THINKING", 30);
        ExamStructureDocument edited = document(lockedBase("10", "\"questionCount\":10", SAME_DIFFICULTY));

        JsonNode sibling = check(exam, edited, ExamCheckScope.from("SECTION", null, "s-reason")).result();
        assertThat(sibling.path("status").asText()).isEqualTo("INVALID");
        assertThat(issue(sibling, "STRUCTURE_COUNT_LOCKED").path("location").path("sectionId").asText())
                .isEqualTo("s-logic");
        assertThat(issueCodes(sibling).stream().filter("STRUCTURE_COUNT_LOCKED"::equals)).hasSize(1);

        JsonNode own = check(exam, edited, ExamCheckScope.from("SECTION", null, "s-logic")).result();
        assertThat(own.path("status").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(own).stream().filter("STRUCTURE_COUNT_LOCKED"::equals)).hasSize(1);

        ExamStructureDocument untouched = document(LOCKED_TEMPLATE);
        JsonNode control = check(exam, untouched, ExamCheckScope.from("SECTION", null, "s-reason")).result();
        assertThat(issueCodes(control)).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    private List<String> sectionStates(Exam exam, ExamStructureDocument document) {
        var snapshots = mock(ExamCheckSnapshotReader.class);
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, catalog.revision(), "hash",
                mapper.createObjectNode());
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null));
        var states = new ExamCheckUnitStates(snapshots, mock(ExamCheckUnitStore.class),
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)),
                new SolverInputBuilder(catalog, source, limits), limits, catalog, mapper, source,
                new ExamCheckTemplateFacts(catalog, templates, mapper));
        return states.states(exam.getId()).stream().map(state -> state.sectionId() + "=" + state.state()).toList();
    }

    // ---- rows 7, 8: TOPIC_QUOTA_MAPPING_MISSING ----------------------------------------------------------

    @Test
    void requiredQuotaWithoutNodesIsLocatedAtItsSectionOrGroup() throws Exception {
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                  {"id":"s","subjectCode":"MATH","questionCount":2,
                   "topicQuotas":[{"id":"q-section","name":"Row","nodeIds":[],"count":1,"enforcement":"REQUIRED"}],
                   "blocks":[{"id":"b","name":"Group","questionType":"SINGLE_CHOICE","questionCount":2,
                     "topicQuotas":[{"id":"q-block","name":"Row","count":1,"enforcement":"REQUIRED"}]}]}]}]}
                """;
        List<ObjectNode> found = of(new ExamCheckRules(mapper, exam(2), document(doc)).unrepresentable(),
                "TOPIC_QUOTA_MAPPING_MISSING");
        assertThat(found).hasSize(2);
        for (ObjectNode issue : found) {
            assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
            assertThat(issue.path("group").asText()).isEqualTo("MATRIX");
            assertThat(issue.path("partId").asText()).isEqualTo("p");
            assertThat(issue.path("sectionId").asText()).isEqualTo("s");
            assertThat(issue.path("subjectCode").asText()).isEqualTo("MATH");
        }
        ObjectNode atSection = found.stream().filter(issue -> issue.path("blockId").isNull()).findFirst().orElseThrow();
        ObjectNode atBlock = found.stream().filter(issue -> !issue.path("blockId").isNull()).findFirst().orElseThrow();
        assertThat(atSection.path("location").has("blockId")).isFalse();
        assertThat(atBlock.path("blockId").asText()).isEqualTo("b");
        assertThat(atBlock.path("location").path("blockId").asText()).isEqualTo("b");
        assertThat(atSection.path("key").asText()).isNotEqualTo(atBlock.path("key").asText());
    }

    @Test
    void requiredUnmappedQuotaFailsTheCheckAndReferenceDoesNot() throws Exception {
        subject("MATH", 2);
        for (String enforcement : List.of("REQUIRED", "REFERENCE")) {
            String doc = """
                    {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                      {"id":"s","subjectCode":"MATH","questionCount":2,
                       "topicQuotas":[{"id":"q","name":"Row","nodeIds":[],"count":1,"enforcement":"%s"}],
                       "blocks":[{"id":"b","name":"Group","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                    """.formatted(enforcement);
            JsonNode result = check(exam(2), document(doc)).result();
            if (enforcement.equals("REQUIRED")) {
                assertThat(result.path("status").asText()).isEqualTo("INVALID");
                assertThat(issueCodes(result)).contains("TOPIC_QUOTA_MAPPING_MISSING");
            } else {
                assertThat(result.path("status").asText()).isEqualTo("VALID");
                assertThat(issueCodes(result)).doesNotContain("TOPIC_QUOTA_MAPPING_MISSING");
            }
        }
    }

    // ---- rows 10, 11: generic infeasibility and "at least" topic rows (tests only, rule unchanged) -------

    @Test
    void levelTotalOfTopicRowsAboveTheSectionLevelCountIsInfeasibleWithoutTopicCode() throws Exception {
        JsonNode result = checkTopicRows(3, 3);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        JsonNode issue = issue(result, "SUBJECT_QUOTA_INFEASIBLE");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("subjectCode").asText()).isEqualTo("MATH");
        assertThat(issueCodes(result)).doesNotContain("TOPIC_QUOTA_INFEASIBLE");
    }

    @Test
    void levelTotalOfTopicRowsBelowTheSectionLevelCountPassesBecauseRowsMeanAtLeast() throws Exception {
        JsonNode result = checkTopicRows(2, 2);
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(issueCodes(result)).doesNotContain("SUBJECT_QUOTA_INFEASIBLE", "TOPIC_QUOTA_INFEASIBLE");
    }

    /** Section of 10 with difficulty NB 5 / TH 5 (REQUIRED) and two REQUIRED mapped NB rows of the given counts. */
    private JsonNode checkTopicRows(int firstNb, int secondNb) throws Exception {
        UUID subjectId = UUID.randomUUID();
        subjects.put("MATH", subjectId);
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) pool.add(single("NB", NODE_A));
        for (int i = 0; i < 4; i++) pool.add(single("NB", NODE_B));
        for (int i = 0; i < 6; i++) pool.add(single("TH", null));
        candidates.put(subjectId, pool);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":10,"sections":[
                  {"id":"s","subjectCode":"MATH","questionCount":10,
                   "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                     "byDifficulty":[{"code":"NB","value":5},{"code":"TH","value":5}]},
                   "topicQuotas":[
                     {"id":"qa","name":"A","nodeIds":["%s"],"count":%d,"basis":"QUESTION","enforcement":"REQUIRED","difficultyCodes":["NB"]},
                     {"id":"qb","name":"B","nodeIds":["%s"],"count":%d,"basis":"QUESTION","enforcement":"REQUIRED","difficultyCodes":["NB"]}],
                   "blocks":[{"id":"b","name":"Group","questionType":"SINGLE_CHOICE","questionCount":10}]}]}]}
                """.formatted(NODE_A, firstNb, NODE_B, secondNb);
        return check(exam(10), document(doc)).result();
    }

    private Candidate single(String difficulty, UUID node) {
        UUID id = UUID.randomUUID();
        return new Candidate(id, 1, null, List.of(new Unit(id, null, QuestionType.SINGLE_CHOICE, "G", difficulty,
                node == null ? List.of() : List.of(node), List.of(), BigDecimal.ONE)), null, null);
    }

    // ---- harness -------------------------------------------------------------------------------------------

    private ExamCheckRules rulesWith(String edited, String locked) throws Exception {
        return rulesWith(document(edited), locked);
    }

    private ExamCheckRules rulesWith(ExamStructureDocument edited, String locked) throws Exception {
        return new ExamCheckRules(mapper, exam(30), edited, Map.of(), Map.of(),
                new ExamCheckRules.TemplateFacts(null, document(locked)));
    }

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document) {
        return check(exam, document, ExamCheckScope.from(null, null, null));
    }

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document, ExamCheckScope scope) {
        CatalogItemView nodeA = node(NODE_A, "/a/");
        CatalogItemView nodeB = node(NODE_B, "/b/");
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(nodeA, nodeB));
        CatalogItemView kind = named("THPTQG");
        CatalogItemView subjectItem = named("VAN");
        when(catalog.findItem(KIND)).thenReturn(Optional.of(kind));
        when(catalog.findItem(SUBJECT_ITEM)).thenReturn(Optional.of(subjectItem));
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
                catalog, source, unitEngine, new ExamCheckTemplateFacts(catalog, templates, mapper));
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, catalog.revision(), "hash",
                mapper.createObjectNode());
        return checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30), scope);
    }

    private static CatalogItemView node(UUID id, String path) {
        CatalogItemView view = mock(CatalogItemView.class);
        when(view.id()).thenReturn(id);
        when(view.path()).thenReturn(path);
        return view;
    }

    private static CatalogItemView named(String code) {
        CatalogItemView view = mock(CatalogItemView.class);
        when(view.code()).thenReturn(code);
        return view;
    }

    private void standardTemplate(boolean publishable) {
        ExamTemplate standard = template(false, publishable, "{\"parts\":[]}");
        when(templates.findBestMatch("THPTQG", "SUBJECT", null, "VAN", null)).thenReturn(standard);
    }

    private ExamTemplate template(boolean fixedCounts, boolean publishable, String structure) {
        try {
            ExamTemplate template = mock(ExamTemplate.class);
            when(template.isFixedCounts()).thenReturn(fixedCounts);
            when(template.isPublishable()).thenReturn(publishable);
            when(template.isDefaultTemplate()).thenReturn(true);
            when(template.getStructure()).thenReturn(mapper.readTree(structure));
            return template;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Exam subjectExam(String scope, String buildMode) {
        Exam exam = exam(1);
        exam.changeScope(KIND, scope, "PART".equals(scope) ? "P3" : null, SUBJECT_ITEM, null, null, true, null,
                "SHARED", 60, null, UUID.randomUUID(), clock.instant());
        exam.setBuildMode(buildMode);
        return exam;
    }

    private Exam fullExam(String templateCode) {
        Exam exam = exam(30);
        exam.applyTemplate(templateCode, null, null, "SHARED", 60, null, UUID.randomUUID(), clock.instant());
        return exam;
    }

    private Exam exam(int count) {
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", KIND, "FULL", "SHARED", 60, null, "RAW",
                clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(count, false, false, 0);
        return exam;
    }

    private void subject(String code, int count) {
        UUID subjectId = UUID.randomUUID();
        subjects.put(code, subjectId);
        List<Candidate> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(single("D", null));
        candidates.put(subjectId, values);
    }

    private static String oneBlock(String subject, int count) {
        return """
                {"parts":[{"id":"p","name":"Part","questionCount":%d,"sections":[{"id":"s","subjectCode":"%s",
                "questionCount":%d,"blocks":[{"id":"b","name":"Block","questionType":"SINGLE_CHOICE","questionCount":%d}]}]}]}
                """.formatted(count, subject, count, count);
    }

    private ExamStructureDocument document(String json) throws Exception {
        return mapper.readValue(json, ExamStructureDocument.class);
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
