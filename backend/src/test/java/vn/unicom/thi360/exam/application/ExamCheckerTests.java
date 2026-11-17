package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamCheckerTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final Map<String, UUID> subjects = new HashMap<>();
    private final Map<UUID, List<Candidate>> candidates = new HashMap<>();
    private final SolverLimits limits = SolverLimits.defaults();

    @Test
    void validFixedPathAndLinearScaleUseRawReferenceMaximum() throws Exception {
        Exam exam = exam(120, new BigDecimal("1200"), "LINEAR");
        exam.applyScoringProfile(new BigDecimal("120"), "LINEAR");
        subject("MATH", 120);
        var result = check(exam, document(singleStructure(120, "1", null)));
        assertThat(result.result().path("status").asText()).isEqualTo("VALID");
        assertThat(result.result().path("groups")).hasSize(8);
        assertThat(result.result().path("paths").get(0).path("totalPoints").decimalValue()).isEqualByComparingTo("120");
        assertThat(result.witness()).hasSize(1);
    }

    @Test
    void fixedScaleRejectsMissingRequiredPoints() throws Exception {
        Exam exam = exam(1, BigDecimal.ONE, "RAW");
        subject("MATH", 1);
        var result = check(exam, document(singleStructure(1, null, null)));
        assertThat(result.result().path("status").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(result.result())).contains("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void scoringMismatchIsAWarningUsingCompactServingCodes() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 0);
        UUID id = UUID.randomUUID();
        var actual = new QuestionSourcePort.ScoringDetails("COUNT_TABLE",
                List.of(new BigDecimal("0.5"), BigDecimal.ONE), null);
        var unit = new Unit(id, null, QuestionType.TRUE_FALSE, "G", "D", List.of(), List.of(),
                BigDecimal.ONE, actual, "Q-SERVING");
        candidates.put(subjects.get("MATH"), List.of(new Candidate(id, 1, null, List.of(unit), null,
                "COUNT_TABLE", "COUNT_TABLE", actual.trueFalseTable(), null, "Q-SERVING")));
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":1,"blocks":[{"id":"b","questionType":"TRUE_FALSE","questionCount":1,
                "expectedScoring":{"trueFalseMethod":"COUNT_TABLE","trueFalseTable":[0.25,1]}}]}]}]}
                """;
        var result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(issueCodes(result)).contains("SCORING_MISMATCH");
        assertThat(result.path("paths").get(0).path("issues").findValues("questionCodes").getFirst().get(0).asText())
                .isEqualTo("Q-SERVING");
        verify(source, never()).loadVersions(any());
    }

    @Test
    void everyClearlyShortGroupIsReportedEvenWhenSolverStopsAtTheFirstSubject() throws Exception {
        Exam exam = exam(4, null, "RAW");
        subject("MATH", 1);
        subject("PHYS", 1);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":4,"sections":[
                {"id":"s1","subjectCode":"MATH","questionCount":2,"blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                {"id":"s2","subjectCode":"PHYS","questionCount":2,"blocks":[{"id":"b2","questionType":"SINGLE_CHOICE","questionCount":2}]}
                ]}]}
                """;
        JsonNode path = check(exam, document(doc)).result().path("paths").get(0);
        assertThat(path.path("issues").findValuesAsText("blockId")).contains("b1", "b2");
        assertThat(path.path("blocks").findValuesAsText("result")).containsExactly("NOT_FOUND", "NOT_FOUND");
    }

    @Test
    void answerQuotaTotalIsDiagnosedSeparatelyFromSectionCount() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        var result = check(exam, document(singleStructure(1, null,
                "\"answerQuotas\":{\"unit\":\"COUNT\",\"items\":[{\"answerType\":\"SINGLE_CHOICE\",\"value\":0}]},")));
        assertThat(issueCodes(result.result())).contains("ANSWER_QUOTA_TOTAL_MISMATCH");
    }

    @Test
    void unrepresentableOptionDoesNotHideValidAlternative() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        for (String bad : List.of(
                "{\"id\":\"bad-b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":0,\"setCount\":0,\"setSize\":2}",
                "{\"id\":\"bad-b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":0,\"setCount\":1,\"setSize\":0}")) {
            String doc = """
                    {"parts":[{"id":"p","questionCount":1,"participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"valid","kind":"SUBJECT","code":"MATH","sections":[{"id":"good","subjectCode":"MATH","questionCount":1,"blocks":[{"id":"good-b","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                    {"id":"bad","kind":"SUBJECT","code":"MATH","sections":[{"id":"bad-s","subjectCode":"MATH","questionCount":0,"blocks":[%s]}]}
                    ]}]}
                    """.formatted(bad);
            JsonNode paths = check(exam, document(doc)).result().path("paths");
            assertThat(paths.get(0).path("result").asText()).isEqualTo("VALID");
            assertThat(paths.get(1).path("issues").findValuesAsText("code")).contains("SET_SIZE_MISMATCH");
        }
        String duplicate = singleStructure(1, null, "\"answerQuotas\":{\"unit\":\"COUNT\",\"items\":["
                + "{\"answerType\":\"SINGLE_CHOICE\",\"value\":1},{\"answerType\":\"SINGLE_CHOICE\",\"value\":0}]},");
        assertThat(issueCodes(check(exam, document(duplicate)).result())).contains("ANSWER_QUOTA_DUPLICATE");
        String duplicateOption = """
                {"parts":[{"id":"p","questionCount":1,"participation":{"mode":"CHOICE","choose":1},"options":[
                {"id":"valid","kind":"SUBJECT","code":"MATH","sections":[{"id":"good","subjectCode":"MATH","questionCount":1,"blocks":[{"id":"good-b","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                {"id":"bad","kind":"SUBJECT","code":"MATH","sections":[{"id":"bad-s","subjectCode":"MATH","questionCount":1,
                "answerQuotas":{"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":1},{"answerType":"SINGLE_CHOICE","value":0}]},
                "blocks":[{"id":"bad-b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]}
                """;
        JsonNode paths = check(exam, document(duplicateOption)).result().path("paths");
        assertThat(paths.get(0).path("result").asText()).isEqualTo("VALID");
        assertThat(paths.get(1).path("issues").findValuesAsText("code")).contains("ANSWER_QUOTA_DUPLICATE");
    }

    @Test
    void fractionalLegacyMatrixIsReportedWithoutCallingTheSource() throws Exception {
        Exam exam = exam(1, null, "RAW");
        String matrix = "\"matrix\":{\"mode\":\"SEPARATE\",\"unit\":\"PERCENT\",\"byGrade\":[{\"code\":\"G\",\"value\":50.5}],\"byDifficulty\":[{\"code\":\"D\",\"value\":100}]},";
        var result = check(exam, document(singleStructure(1, null, matrix)));
        assertThat(issueCodes(result.result())).contains("MATRIX_NON_INTEGER");
        assertThat(result.result().path("groups").findValuesAsText("group")).contains("MATRIX");
        verify(source, never()).findCandidates(any(), anyInt());
    }

    @Test
    void separateMatrixTotalsAndNegativeValuesAreReported() throws Exception {
        Exam exam = exam(10, null, "RAW");
        subject("MATH", 10);
        String percent = "\"matrix\":{\"mode\":\"SEPARATE\",\"unit\":\"PERCENT\","
                + "\"byGrade\":[{\"code\":\"G\",\"value\":90}],\"byDifficulty\":[{\"code\":\"D\",\"value\":100}]},";
        assertThat(issueCodes(check(exam, document(singleStructure(10, null, percent))).result()))
                .contains("PERCENT_TOTAL_INVALID");
        String negative = "\"matrix\":{\"mode\":\"COMBINED\",\"unit\":\"COUNT\","
                + "\"cells\":[{\"gradeCode\":\"G\",\"difficultyCode\":\"D\",\"value\":-1}]},";
        assertThat(issueCodes(check(exam, document(singleStructure(10, null, negative))).result()))
                .contains("MATRIX_NEGATIVE");
    }

    @Test
    void sharedTimingAndEmptyPartsBlockTheirPaths() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        String timed = singleStructure(1, null, null).replace("\"name\":\"Part\"", "\"name\":\"Part\",\"durationMinutes\":30");
        JsonNode result = check(exam, document(timed)).result();
        assertThat(result.path("paths").get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(result)).contains("SHARED_PART_DURATION");
        JsonNode empty = check(exam(0, null, "RAW"), document("{\"parts\":[{\"id\":\"p\",\"questionCount\":0}]}")).result();
        assertThat(empty.path("paths").get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(empty)).contains("PART_EMPTY");
    }

    @Test
    void sectionAndPathTotalsUseActualGroupCounts() throws Exception {
        Exam exam = exam(12, null, "RAW");
        subject("MATH", 10);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":12,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":12,"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":10}]}]}]}
                """;
        assertThat(issueCodes(check(exam, document(doc)).result()))
                .contains("SECTION_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH");
    }

    @Test
    void incompleteOptionLevelRemainderIsReportedRatherThanThrowing() throws Exception {
        Exam exam = exam(1, null, "RAW");
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                "options":[{"id":"first","kind":"SUBJECT","code":"MATH",
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE"}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(result)).contains("SECTION_TOTAL_MISMATCH");
    }

    @Test
    void omittedEssayIsReportedWithoutAffectingTotalsOrRequiringManualGrading() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        String doc = """
                {"parts":[{"id":"p","questionCount":1,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":1,"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1},
                {"id":"essay","questionType":"ESSAY","questionCount":1,"omitted":true}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(issueCodes(result)).contains("OMITTED_GROUPS_PRESENT").doesNotContain("MANUAL_GRADING_REQUIRED");
        JsonNode issue = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(value -> "OMITTED_GROUPS_PRESENT".equals(value.path("code").asText()))
                .findFirst().orElseThrow();
        assertThat(issue.path("location").path("blockId").asText()).isEqualTo("essay");
    }

    @Test
    void incompleteChildBoundsAreReportedBeforeConstructingSolverInput() throws Exception {
        Exam exam = exam(1, null, "RAW");
        String doc = """
                {"parts":[{"id":"p","questionCount":1,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":1,"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1,
                "childConditions":[{"from":1}]}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        assertThat(issueCodes(result)).contains("SECTION_INCOMPLETE");
        verify(source, never()).findCandidates(any(), anyInt());
    }

    @Test
    void selectedOptionCombinationDeterminesPartTotal() throws Exception {
        Exam exam = exam(2, null, "RAW");
        for (String code : List.of("A", "AA", "B", "BB", "FIX")) subject(code, 2);
        String choice = """
                {"id":"%s","kind":"SUBJECT_CHOICE","code":"%s","chooseSubjects":1,"countsByPick":[1],
                "sections":[%s,%s]}
                """;
        String first = choice.formatted("first", "FIRST", scienceSection("A", "NATURAL"), scienceSection("AA", "NATURAL"));
        String second = choice.formatted("second", "SECOND", scienceSection("B", "NATURAL"), scienceSection("BB", "NATURAL"));
        String regular = """
                {"id":"fixed","kind":"SUBJECT","code":"FIX","sections":[{"id":"fixed-s","subjectCode":"FIX",
                "questionCount":1,"blocks":[{"id":"fixed-b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}
                """;
        for (String options : List.of(first + "," + second, first + "," + regular)) {
            String doc = """
                    {"parts":[{"id":"p","questionCount":2,"participation":{"mode":"CHOICE","choose":2},"options":[%s]}]}
                    """.formatted(options);
            JsonNode result = check(exam, document(doc)).result();
            assertThat(result.path("status").asText()).isEqualTo("VALID");
            assertThat(issueCodes(result)).doesNotContain("SUBJECT_CHOICE_COUNTS_INVALID", "PART_TOTAL_MISMATCH");
        }
    }

    @Test
    void malformedChoiceDoesNotHideASeparateValidPath() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                "options":[
                {"id":"valid","kind":"SUBJECT","code":"MATH","sections":[{"id":"s","subjectCode":"MATH","questionCount":1,
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                {"id":"bad","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":3,"countsByPick":[1],"sections":[]}
                ]}]}
                """;
        JsonNode paths = check(exam, document(doc)).result().path("paths");
        assertThat(paths).hasSize(2);
        assertThat(paths.get(0).path("result").asText()).isEqualTo("VALID");
        assertThat(paths.get(1).path("result").asText()).isEqualTo("INVALID");
        assertThat(paths.get(1).path("issues").findValuesAsText("code")).contains("SUBJECT_CHOICE_COUNTS_INVALID");
    }

    @Test
    void legacyFractionInOneOptionDoesNotHideASeparateValidPath() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                "options":[
                {"id":"valid","kind":"SUBJECT","code":"MATH","sections":[{"id":"s","subjectCode":"MATH","questionCount":1,
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                {"id":"bad","kind":"SUBJECT","code":"MATH","sections":[{"id":"bad-s","subjectCode":"MATH","questionCount":1,
                "matrix":{"mode":"COMBINED","unit":"COUNT","cells":[{"gradeCode":"G","difficultyCode":"D","value":0.5}]},
                "blocks":[{"id":"bad-b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}
                ]}]}
                """;
        JsonNode paths = check(exam, document(doc)).result().path("paths");
        assertThat(paths).hasSize(2);
        assertThat(paths.get(0).path("result").asText()).isEqualTo("VALID");
        assertThat(paths.get(1).path("result").asText()).isEqualTo("INVALID");
        assertThat(paths.get(1).path("issues").findValuesAsText("code")).contains("MATRIX_NON_INTEGER");
    }

    @Test
    void unconfirmedOptionDoesNotInvalidateAnotherChoice() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        String doc = """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                "options":[
                {"id":"first","kind":"SUBJECT","code":"MATH","expertConfirmed":false,
                 "sections":[{"id":"s1","subjectCode":"MATH","questionCount":1,"blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                {"id":"second","kind":"SUBJECT","code":"MATH",
                 "sections":[{"id":"s2","subjectCode":"MATH","questionCount":1,"blocks":[{"id":"b2","questionType":"SINGLE_CHOICE","questionCount":1}]}]}
                ]}]}
                """;
        JsonNode paths = check(exam, document(doc)).result().path("paths");
        assertThat(paths.get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(paths.get(0).path("issues").findValuesAsText("code")).contains("OPTION_UNCONFIRMED");
        assertThat(paths.get(1).path("result").asText()).isEqualTo("VALID");
    }

    @Test
    void scienceChoiceCountsMustFillThePartAfterCommonSections() throws Exception {
        Exam exam = exam(50, null, "RAW");
        String sections = scienceSection("PHY", "NATURAL") + ","
                + scienceSection("CHEM", "NATURAL") + ","
                + scienceSection("BIO", "NATURAL");
        String doc = """
                {"parts":[{"id":"p","questionCount":50,"participation":{"mode":"CHOICE","choose":1},
                "options":[{"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE",
                "chooseSubjects":3,"countsByPick":[17,17,17],"sections":[%s]}]}]}
                """.formatted(sections);
        JsonNode result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(result)).contains("SUBJECT_CHOICE_COUNTS_INVALID");
    }

    @Test
    void scienceReportsAllTenSelectionsAndTheSeventeenQuestionShortfall() throws Exception {
        Exam exam = exam(50, null, "RAW");
        subject("ENGLISH", 50);
        subject("PHY", 16);
        for (String name : List.of("CHEM", "BIO", "HISTORY", "GEO")) subject(name, 17);
        String sections = scienceSection("PHY", "NATURAL") + "," + scienceSection("CHEM", "NATURAL") + ","
                + scienceSection("BIO", "NATURAL") + "," + scienceSection("HISTORY", "SOCIAL") + ","
                + scienceSection("GEO", "SOCIAL");
        String doc = """
                {"parts":[{"id":"p","order":1,"name":"Part","questionCount":50,
                "participation":{"mode":"CHOICE","choose":1},"options":[
                {"id":"english","kind":"SUBJECT","code":"ENGLISH","sections":[{"id":"en","subjectCode":"ENGLISH","questionCount":50,"blocks":[{"id":"en-block","questionType":"SINGLE_CHOICE","questionCount":50}]}]},
                {"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":3,"countsByPick":[17,17,16],"sections":[%s]}
                ]}]}
                """.formatted(sections);
        var result = check(exam, document(doc));
        JsonNode english = result.result().path("paths").get(0);
        JsonNode science = result.result().path("paths").get(1);
        assertThat(english.path("result").asText()).isEqualTo("VALID");
        assertThat(science.path("result").asText()).isEqualTo("INVALID");
        assertThat(science.path("selections")).hasSize(10);
        assertThat(science.path("issues").findValuesAsText("subjectCode")).contains("PHY");
        assertThat(science.path("issues").findValues("subjectCount").stream().anyMatch(value -> value.asInt() == 17)).isTrue();
    }

    @Test
    void sciencePointsAreCheckedForEachFullCountSelection() throws Exception {
        Exam exam = exam(50, new BigDecimal("50"), "RAW");
        for (String code : List.of("A", "B", "C", "D", "E")) subject(code, 17);
        List<String> sections = new ArrayList<>();
        for (String code : List.of("A", "B", "C", "D", "E")) {
            boolean natural = List.of("A", "B", "C").contains(code);
            sections.add(scienceSection(code, natural ? "NATURAL" : "SOCIAL")
                    .replace("\"questionType\":\"SINGLE_CHOICE\"", "\"questionType\":\"SINGLE_CHOICE\",\"requiredPointsPerQuestion\":" + (natural ? "1" : "2")));
        }
        String doc = """
                {"parts":[{"id":"p","questionCount":50,"participation":{"mode":"CHOICE","choose":1},
                "options":[{"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":3,
                "countsByPick":[17,17,16],"sections":[%s]}]}]}
                """.formatted(String.join(",", sections));
        JsonNode path = check(exam, document(doc)).result().path("paths").get(0);
        assertThat(path.path("result").asText()).isEqualTo("INVALID");
        assertThat(path.path("selections")).hasSize(10);
        for (JsonNode selection : path.path("selections")) {
            assertThat(selection.path("counts")).hasSize(3);
            for (JsonNode count : selection.path("counts")) assertThat(count.asInt()).isEqualTo(17);
            int social = 0;
            for (JsonNode code : selection.path("subjectCodes")) {
                if (List.of("D", "E").contains(code.asText())) social++;
            }
            if (social == 0) {
                assertThat(selection.path("result").asText()).isEqualTo("VALID");
            } else {
                assertThat(selection.path("result").asText()).isEqualTo("INVALID");
                JsonNode points = java.util.stream.StreamSupport.stream(selection.path("issues").spliterator(), false)
                        .filter(issue -> "POINTS_TOTAL_MISMATCH".equals(issue.path("code").asText()))
                        .findFirst().orElseThrow();
                assertThat(points.path("available").asInt()).isEqualTo(50 + 17 * social - (social == 1 ? 1 : 0));
            }
        }
    }

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document) {
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of());
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
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper);
        return checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30));
    }

    private Exam exam(int count, BigDecimal max, String scoring) {
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", null, "FULL", "SHARED", 60,
                max, scoring, clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(count, false, false, 0);
        return exam;
    }

    private void subject(String code, int count) {
        UUID subjectId = UUID.randomUUID();
        subjects.put(code, subjectId);
        List<Candidate> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            values.add(new Candidate(id, 1, null,
                    List.of(new Unit(id, null, QuestionType.SINGLE_CHOICE, "G", "D", List.of(), List.of(), BigDecimal.ONE)), null, null));
        }
        candidates.put(subjectId, values);
    }

    private ExamStructureDocument document(String json) throws Exception { return mapper.readValue(json, ExamStructureDocument.class); }

    private static String singleStructure(int count, String points, String extra) {
        return """
                {"parts":[{"id":"p","order":1,"name":"Part","questionCount":%d,"sections":[{"id":"s","subjectCode":"MATH","questionCount":%d,%s
                "blocks":[{"id":"b","name":"Block","questionType":"SINGLE_CHOICE","questionCount":%d%s}]}]}]}
                """.formatted(count, count, extra == null ? "" : extra, count,
                points == null ? "" : ",\"requiredPointsPerQuestion\":" + points);
    }

    private static String scienceSection(String code, String group) {
        return "{\"id\":\"" + code + "\",\"subjectCode\":\"" + code + "\",\"choiceGroup\":\"" + group
                + "\",\"blocks\":[{\"id\":\"" + code + "-block\",\"questionType\":\"SINGLE_CHOICE\"}]}";
    }

    private static List<String> issueCodes(JsonNode result) { return result.path("groups").findValuesAsText("code"); }
}
