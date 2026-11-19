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
    void flexibleSetsAvoidSizeMismatchAndImpossibleTotalReportsSetShortage() throws Exception {
        Exam exam = exam(5, null, "RAW");
        subject("MATH", 0);
        UUID root = UUID.randomUUID();
        List<Unit> children = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            children.add(new Unit(UUID.randomUUID(), null, QuestionType.SINGLE_CHOICE,
                    "G", "D", List.of(), List.of(), BigDecimal.ONE, null, "C" + i));
        }
        candidates.put(subjects.get("MATH"), List.of(new Candidate(root, 1, root, children, null, null)));
        String doc = """
                {"parts":[{"id":"p","questionCount":5,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":5,"blocks":[{"id":"b","questionType":"QUESTION_SET","questionCount":5}]}]}]}
                """;
        var result = check(exam, document(doc)).result();
        assertThat(issueCodes(result)).doesNotContain("SET_SIZE_MISMATCH").contains("SET_SHORTAGE");
        assertThat(result.path("paths").get(0).path("issues").findValuesAsText("blockId")).contains("b");
    }

    @Test
    void flexibleSetsAreValidWithRequiredSectionDimensions() throws Exception {
        Exam exam = exam(18, null, "RAW");
        subject("MATH", 0);
        List<Candidate> sets = new ArrayList<>();
        for (int size : List.of(3, 4, 5, 3, 4, 5)) {
            UUID root = UUID.randomUUID();
            List<Unit> children = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                children.add(new Unit(UUID.randomUUID(), null, QuestionType.SINGLE_CHOICE,
                        "G", "D", List.of(), List.of(), BigDecimal.ONE, null, "C" + i));
            }
            sets.add(new Candidate(root, 1, root, children, null, null));
        }
        candidates.put(subjects.get("MATH"), sets);
        String doc = """
                {"parts":[{"id":"p","questionCount":18,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":18,"matrix":{"mode":"SEPARATE","unit":"COUNT",
                "gradeEnforcement":"REQUIRED","difficultyEnforcement":"REQUIRED",
                "byGrade":[{"code":"G","value":18}],"byDifficulty":[{"code":"D","value":18}]},
                "blocks":[{"id":"b","questionType":"QUESTION_SET","questionCount":18}]}]}]}
                """;
        var checked = check(exam, document(doc));
        assertThat(checked.result().path("status").asText()).isEqualTo("VALID");
        assertThat(checked.witness()).hasSize(1);
    }

    @Test
    void incompleteOrMismatchedSetDimensionsStillReportMismatch() throws Exception {
        Exam exam = exam(5, null, "RAW");
        subject("MATH", 0);
        for (String dimensions : List.of("\"setCount\":1", "\"setCount\":2,\"setSize\":3")) {
            String doc = """
                    {"parts":[{"id":"p","questionCount":5,"sections":[{"id":"s","subjectCode":"MATH",
                    "questionCount":5,"blocks":[{"id":"b","questionType":"QUESTION_SET","questionCount":5,%s}]}]}]}
                    """.formatted(dimensions);
            assertThat(issueCodes(check(exam, document(doc)).result())).contains("SET_SIZE_MISMATCH");
        }
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
    void referenceMatricesComparePromptAndPointsAgainstTheProof() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 0);
        UUID id = UUID.randomUUID();
        Unit unit = new Unit(id, null, QuestionType.TRUE_FALSE, "G12", "D", List.of(), List.of(),
                new BigDecimal("2"), null, "TF", 4, 4, null, null, null, false);
        candidates.put(subjects.get("MATH"), List.of(new Candidate(id, 1, null, List.of(unit), null, null)));
        for (String basis : List.of("PROMPT", "POINTS")) {
            int amount = basis.equals("PROMPT") ? 4 : 2;
            for (int required : List.of(amount, amount + 1)) {
                String matrix = """
                        "matrix":{"mode":"SEPARATE","unit":"COUNT","basis":"%s",
                        "gradeEnforcement":"REFERENCE","difficultyEnforcement":"REFERENCE",
                        "byGrade":[{"code":"G12","value":%d}]},
                        """.formatted(basis, required);
                String doc = singleStructure(1, null, matrix).replace("SINGLE_CHOICE", "TRUE_FALSE");
                JsonNode result = check(exam, document(doc)).result();
                assertThat(result.path("status").asText()).isEqualTo("VALID");
                assertThat(issueCodes(result)).doesNotContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
                if (required == amount) assertThat(issueCodes(result)).doesNotContain("DISTRIBUTION_DEVIATION");
                else assertThat(issueCodes(result)).contains("DISTRIBUTION_DEVIATION");
                JsonNode row = result.path("paths").get(0).path("subjects").get(0).path("byGrade").get(0);
                assertThat(row.path("required").asInt()).isEqualTo(required);
                assertThat(row.path("achieved").asInt()).isEqualTo(amount);
            }
        }
    }

    @Test
    void provedZeroReferenceBucketDoesNotUseTheSourcePool() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 0);
        UUID g10 = UUID.randomUUID(), g12 = UUID.randomUUID();
        candidates.put(subjects.get("MATH"), List.of(
                new Candidate(g10, 1, null, List.of(new Unit(g10, null, QuestionType.SINGLE_CHOICE,
                        "G10", "D10", List.of(), List.of(), BigDecimal.ONE)), null, null),
                new Candidate(g12, 1, null, List.of(new Unit(g12, null, QuestionType.SINGLE_CHOICE,
                        "G12", "D12", List.of(), List.of(), BigDecimal.ONE)), null, null)));
        String matrix = """
                "matrix":{"mode":"SEPARATE","unit":"COUNT","basis":"QUESTION",
                "gradeEnforcement":"REFERENCE","difficultyEnforcement":"REQUIRED",
                "byGrade":[{"code":"G10","value":1}],"byDifficulty":[{"code":"D12","value":1}]},
                """;
        JsonNode result = check(exam, document(singleStructure(1, null, matrix))).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        JsonNode row = result.path("paths").get(0).path("subjects").get(0).path("byGrade").get(0);
        assertThat(row.path("code").asText()).isEqualTo("G10");
        assertThat(row.path("required").asInt()).isEqualTo(1);
        assertThat(row.path("achieved").asInt()).isZero();
        JsonNode warning = java.util.stream.StreamSupport.stream(result.path("groups").spliterator(), false)
                .flatMap(group -> java.util.stream.StreamSupport.stream(group.path("issues").spliterator(), false))
                .filter(issue -> "DISTRIBUTION_DEVIATION".equals(issue.path("code").asText())).findFirst().orElseThrow();
        assertThat(warning.path("available").asInt()).isZero();
    }

    @Test
    void pointReferenceRowsAndWarningsPreserveDecimalValues() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 0);
        UUID id = UUID.randomUUID();
        Unit unit = new Unit(id, null, QuestionType.SINGLE_CHOICE, "G12", "D", List.of(), List.of(),
                new BigDecimal("0.25"));
        candidates.put(subjects.get("MATH"), List.of(new Candidate(id, 1, null, List.of(unit), null, null)));
        for (String matrix : List.of(
                """
                {"mode":"SEPARATE","unit":"COUNT","basis":"POINTS","gradeEnforcement":"REFERENCE",
                 "byGrade":[{"code":"G12","value":0.25}]}
                """,
                """
                {"mode":"COMBINED","unit":"COUNT","basis":"POINTS","gradeEnforcement":"REFERENCE",
                 "difficultyEnforcement":"REFERENCE","cells":[{"gradeCode":"G12","difficultyCode":"D","value":0.25}]}
                """)) {
            JsonNode result = check(exam, document(singleStructure(1, null, "\"matrix\":" + matrix + ","))).result();
            assertThat(result.path("status").asText()).isEqualTo("VALID");
            assertThat(issueCodes(result)).doesNotContain("DISTRIBUTION_DEVIATION", "MATRIX_NON_INTEGER");
            JsonNode row = result.path("paths").get(0).path("subjects").get(0).path("byGrade").get(0);
            assertThat(row.path("required").decimalValue()).isEqualByComparingTo("0.25");
            assertThat(row.path("achieved").decimalValue()).isEqualByComparingTo("0.25");
        }
        String percent = """
                "matrix":{"mode":"SEPARATE","unit":"PERCENT","basis":"POINTS","gradeEnforcement":"REFERENCE",
                "byGrade":[{"code":"G12","value":50},{"code":"G10","value":50}]},
                """;
        JsonNode result = check(exam, document(singleStructure(1, null, percent))).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        JsonNode row = result.path("paths").get(0).path("subjects").get(0).path("byGrade").get(0);
        assertThat(row.path("required").decimalValue()).isEqualByComparingTo("0.125");
        assertThat(row.path("achieved").decimalValue()).isEqualByComparingTo("0.25");
        JsonNode warning = java.util.stream.StreamSupport.stream(result.path("groups").spliterator(), false)
                .flatMap(group -> java.util.stream.StreamSupport.stream(group.path("issues").spliterator(), false))
                .filter(issue -> "DISTRIBUTION_DEVIATION".equals(issue.path("code").asText())
                        && "G12".equals(issue.path("gradeCode").asText())).findFirst().orElseThrow();
        assertThat(warning.path("required").decimalValue()).isEqualByComparingTo("0.125");
        assertThat(warning.path("available").decimalValue()).isEqualByComparingTo("0.25");
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
    void partContainingOnlyOmittedEssayIsNotAnEmptyDraftPart() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        subject("VAN", 0);
        String doc = """
                {"parts":[{"id":"p1","questionCount":1,"sections":[{"id":"s1","subjectCode":"MATH",
                "questionCount":1,"blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                {"id":"p2","questionCount":0,"sections":[{"id":"s2","subjectCode":"VAN","questionCount":0,
                "blocks":[{"id":"essay","questionType":"ESSAY","questionCount":1,"omitted":true}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(issueCodes(result)).contains("OMITTED_GROUPS_PRESENT").doesNotContain("PART_EMPTY");
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
    void invalidChildRangesAreLocatedBeforeConstructingSolverInput() throws Exception {
        Exam exam = exam(1, null, "RAW");
        for (String range : List.of("{\"from\":1,\"to\":21}", "{\"from\":0,\"to\":1}", "{\"from\":2,\"to\":1}")) {
            String doc = """
                    {"parts":[{"id":"p","questionCount":1,"sections":[{"id":"s","subjectCode":"MATH",
                    "questionCount":1,"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1,
                    "childConditions":[%s]}]}]}]}
                    """.formatted(range);
            JsonNode result = check(exam, document(doc)).result();
            JsonNode issue = java.util.stream.StreamSupport.stream(result.path("groups").spliterator(), false)
                    .flatMap(group -> java.util.stream.StreamSupport.stream(group.path("issues").spliterator(), false))
                    .filter(value -> "SECTION_INCOMPLETE".equals(value.path("code").asText())).findFirst().orElseThrow();
            assertThat(issue.path("location").path("partId").asText()).isEqualTo("p");
            assertThat(issue.path("location").path("sectionId").asText()).isEqualTo("s");
            assertThat(issue.path("location").path("blockId").asText()).isEqualTo("b");
        }
        verify(source, never()).findCandidates(any(), anyInt());
    }

    @Test
    void emptyReviewStyleDraftReportsMissingAnswerTypeAndPart() throws Exception {
        Exam exam = exam(5, null, "RAW");
        subject("MATH", 0);
        String doc = """
                {"parts":[{"id":"p","questionCount":5,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":5,"blocks":[{"id":"b","questionCount":0}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(issueCodes(result)).contains("GROUP_ANSWER_TYPE_MISSING", "PART_EMPTY");
    }

    @Test
    void codeSourceSubjectShortageListsOnlyCodesContainingThatSubject() throws Exception {
        Exam exam = exam(2, null, "RAW");
        subject("PHY", 1);
        subject("CHEM", 0);
        subject("BIO", 1);
        var first = mock(vn.unicom.thi360.catalog.spi.CatalogCombinationView.class);
        when(first.itemCode()).thenReturn("QD01");
        when(first.subjectCodes()).thenReturn(List.of("PHY", "CHEM"));
        var second = mock(vn.unicom.thi360.catalog.spi.CatalogCombinationView.class);
        when(second.itemCode()).thenReturn("QD02");
        when(second.subjectCodes()).thenReturn(List.of("CHEM", "BIO"));
        var third = mock(vn.unicom.thi360.catalog.spi.CatalogCombinationView.class);
        when(third.itemCode()).thenReturn("QD03");
        when(third.subjectCodes()).thenReturn(List.of("PHY", "BIO"));
        when(catalog.listCombinations(null)).thenReturn(List.of(first, second, third));
        String doc = """
                {"parts":[{"id":"p","questionCount":2,"codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                {"id":"phy","subjectCode":"PHY","questionCount":1,"blocks":[{"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1}]},
                {"id":"chem","subjectCode":"CHEM","questionCount":1,"blocks":[{"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]},
                {"id":"bio","subjectCode":"BIO","questionCount":1,"blocks":[{"id":"bb","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """;
        JsonNode result = check(exam, document(doc)).result();
        JsonNode issue = java.util.stream.StreamSupport.stream(result.path("groups").spliterator(), false)
                .flatMap(group -> java.util.stream.StreamSupport.stream(group.path("issues").spliterator(), false))
                .filter(value -> "CHEM".equals(value.path("subjectCode").asText()))
                .findFirst().orElseThrow();
        assertThat(issue.path("affectedCodes")).extracting(value -> value.asText())
                .containsExactlyInAnyOrder("QD01", "QD02");
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
        assertThat(path.path("totalQuestions").asInt()).isEqualTo(50);
        assertThat(path.path("totalPoints").decimalValue()).isEqualByComparingTo("50");
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

    /** CR-4 R1-12: only a check that reached the paths vouches for the rules; a spent budget must not. */
    @Test
    void checkWithoutEvaluatedPathsDoesNotVouchForTheRules() throws Exception {
        Exam exam = exam(1, BigDecimal.ONE, "RAW");
        subject("MATH", 1);
        ExamStructureDocument document = document(singleStructure(1, "1", null));
        var complete = check(exam, document);
        assertThat(complete.rulesEvaluated()).isTrue();
        assertThat(complete.result().path("paths")).isNotEmpty();

        var spent = check(exam, document, clock.instant());
        assertThat(spent.rulesEvaluated()).isFalse();
        assertThat(spent.result().path("paths")).isEmpty();
        assertThat(spent.result().path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void unconfirmedPathOrSelectionCannotVouchForRulesEvenWhenOverallOutcomeIsInvalid() {
        var path = vn.unicom.thi360.exam.domain.solver.SolvePath.of(0, List.of());
        var invalid = vn.unicom.thi360.exam.domain.solver.SolveStatus.INVALID;
        var uncertain = vn.unicom.thi360.exam.domain.solver.SolveStatus.UNCONFIRMED;
        var incompletePath = new vn.unicom.thi360.exam.domain.solver.PathResult(
                path, uncertain, null, null, null, List.of(), List.of(), null);
        var invalidPath = new vn.unicom.thi360.exam.domain.solver.PathResult(
                path, invalid, null, null, null, List.of(), List.of(), null);
        var outcome = new vn.unicom.thi360.exam.domain.solver.SolveOutcome(
                invalid, null, List.of(invalidPath, incompletePath), null);
        assertThat(ExamCheckAssembler.rulesEvaluated(outcome)).isFalse();

        var selection = new vn.unicom.thi360.exam.domain.solver.SelectionResult(
                List.of(), uncertain, null, null, null, null, List.of());
        var mixed = new vn.unicom.thi360.exam.domain.solver.PathResult(
                path, invalid, null, null, null, List.of(), List.of(), null, List.of(selection));
        assertThat(ExamCheckAssembler.rulesEvaluated(new vn.unicom.thi360.exam.domain.solver.SolveOutcome(
                invalid, null, List.of(mixed), null))).isFalse();
        assertThat(ExamCheckAssembler.rulesEvaluated(new vn.unicom.thi360.exam.domain.solver.SolveOutcome(
                invalid, null, List.of(invalidPath), null))).isTrue();
    }

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document) {
        return check(exam, document, clock.instant().plusSeconds(30));
    }

    private ExamChecker.Evaluation check(Exam exam, ExamStructureDocument document, Instant deadline) {
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
        var unitStore = mock(ExamCheckUnitStore.class);
        when(unitStore.load(exam.getId())).thenReturn(java.util.Map.of());
        var unitEngine = new ExamCheckUnitEngine(unitStore,
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)), catalog, mapper, limits, clock);
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper, catalog, source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), unitEngine, new ExamCheckTemplateFacts(catalog, mock(ExamTemplateService.class), mapper));
        var input = new ExamCheckResultStore.Input(exam.getId(), exam.getRevision() == null ? 0 : exam.getRevision(), "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, catalog.revision(), "hash", mapper.createObjectNode());
        return checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null),
                deadline, Duration.ofSeconds(30), ExamCheckScope.from(null, null, null));
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
