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
    void answerQuotaTotalIsDiagnosedSeparatelyFromSectionCount() throws Exception {
        Exam exam = exam(1, null, "RAW");
        subject("MATH", 1);
        var result = check(exam, document(singleStructure(1, null,
                "\"answerQuotas\":{\"unit\":\"COUNT\",\"items\":[{\"answerType\":\"SINGLE_CHOICE\",\"value\":0}]},")));
        assertThat(issueCodes(result.result())).contains("ANSWER_QUOTA_TOTAL_MISMATCH");
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
    void scienceReportsAllTwelveSelectionsAndTheSeventeenQuestionShortfall() throws Exception {
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
        assertThat(science.path("selections")).hasSize(12);
        assertThat(science.path("issues").findValuesAsText("subjectCode")).contains("PHY");
        assertThat(science.path("issues").findValues("subjectCount").stream().anyMatch(value -> value.asInt() == 17)).isTrue();
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
