package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamCheckDistributionTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void referenceGradeDeviationWarnsWithRequiredAndActual() throws Exception {
        var document = document("""
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":2,"matrix":{"mode":"SEPARATE","unit":"COUNT",
                "byGrade":[{"code":"G10","value":1}],"gradeEnforcement":"REFERENCE",
                "difficultyEnforcement":"REFERENCE"},"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        var path = path("MATH", "s", 2, List.of(pick("q1"), pick("q2")));
        var report = analyze(document, path, List.of());
        assertThat(codes(report)).containsExactly("DISTRIBUTION_DEVIATION");
        ObjectNode issue = report.issues().getFirst();
        assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
        assertThat(issue.path("dimension").asText()).isEqualTo("GRADE");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G10");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asInt()).isEqualTo(0);
        assertThat(issue.path("sectionId").asText()).isEqualTo("s");
        assertThat(issue.path("partId").asText()).isEqualTo("p");
        // Actual rows come from the proof paper, not the source pool.
        var counts = report.sectionActuals().get("s").get(2).basis(vn.unicom.thi360.exam.domain.QuotaBasis.QUESTION);
        assertThat(counts.grade().get("G12")).isEqualByComparingTo("2");
        assertThat(counts.total()).isEqualByComparingTo("2");
    }

    @Test
    void fractionalPointWeightsRemainExactInReferenceComparisons() throws Exception {
        var document = document("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":1,"matrix":{"mode":"SEPARATE","unit":"COUNT","basis":"POINTS",
                "byGrade":[{"code":"G12","value":1}],"gradeEnforcement":"REFERENCE"},
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        var path = path("MATH", "s", 1, List.of(pick("q1")));
        var unit = new QuestionSourcePort.Unit(UUID.randomUUID(), null, QuestionType.SINGLE_CHOICE,
                "G12", "D", List.of(), List.of(), new BigDecimal("0.75"));
        var picked = new ExamCheckDistribution.PickedUnit("MATH", "s", 1, true, "b", unit);
        var report = ExamCheckDistribution.analyze(document,
                new ExamCheckDistribution.PathEvidence(path, "default", List.of(), List.of(picked)), mapper);
        assertThat(report.issues()).hasSize(1);
        assertThat(report.issues().getFirst().path("available").decimalValue()).isEqualByComparingTo("0.75");
        assertThat(report.sectionActuals().get("s").get(1)
                .basis(vn.unicom.thi360.exam.domain.QuotaBasis.POINTS).grade().get("G12"))
                .isEqualByComparingTo("0.75");
    }

    @Test
    void matchingReferenceDistributionAndRequiredMatrixDoNotWarn() throws Exception {
        var document = document("""
                {"parts":[{"id":"p","questionCount":2,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":2,"matrix":{"mode":"SEPARATE","unit":"COUNT",
                "byGrade":[{"code":"G12","value":2}],"gradeEnforcement":"REFERENCE",
                "byDifficulty":[{"code":"EASY","value":1}],
                "difficultyEnforcement":"REQUIRED"},"blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        var path = path("MATH", "s", 2, List.of(pick("q1"), pick("q2")));
        var report = analyze(document, path, List.of());
        assertThat(codes(report)).isEmpty();
    }

    @Test
    void blockLevelReferenceMatrixWarnsAgainstItsOwnPicksOnly() throws Exception {
        var document = document("""
                {"parts":[{"id":"p","questionCount":4,"sections":[{"id":"s","subjectCode":"MATH",
                "questionCount":4,"blocks":[
                {"id":"b1","questionType":"SINGLE_CHOICE","questionCount":2,"matrix":{"mode":"SEPARATE","unit":"COUNT",
                  "byGrade":[{"code":"G10","value":2}],"gradeEnforcement":"REFERENCE","difficultyEnforcement":"REFERENCE"}},
                {"id":"b2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        var g1 = new GroupQuota("b1", GroupQuota.GroupKind.SINGLE, 2, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        var g2 = new GroupQuota("b2", GroupQuota.GroupKind.SINGLE, 2, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        var section = new SectionOnPath("MATH", "s", 4, null, List.of(g1, g2));
        var witness = new SubjectWitness("MATH", 4, List.of(
                new GroupWitness("b1", List.of(pick("q1"), pick("q2"))),
                new GroupWitness("b2", List.of(pick("q3"), pick("q4")))));
        var result = new PathResult(SolvePath.of(0, List.of(section)), SolveStatus.VALID, null, null, null,
                List.of(witness), List.of(), null);
        Map<String, QuestionSourcePort.Candidate> candidates = Map.of(
                "q1:1", candidate("q1", "G12"), "q2:1", candidate("q2", "G12"),
                "q3:1", candidate("q3", "G10"), "q4:1", candidate("q4", "G10"));
        var units = ExamCheckDistribution.pickedUnits(result, candidates);
        var evidence = new ExamCheckDistribution.PathEvidence(result, "default", List.of(), units);
        var report = ExamCheckDistribution.analyze(document, evidence, mapper);
        assertThat(codes(report)).containsExactly("DISTRIBUTION_DEVIATION");
        ObjectNode issue = report.issues().getFirst();
        assertThat(issue.path("blockId").asText()).isEqualTo("b1");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G10");
        assertThat(issue.path("required").asInt()).isEqualTo(2);
        assertThat(issue.path("available").asInt()).isEqualTo(0);
    }

    @Test
    void matchingExamQuotasDoNotWarn() throws Exception {
        var document = document("""
                {"examQuotas":[{"id":"q1","dimension":"GRADE","basis":"QUESTION","enforcement":"REFERENCE",
                "subjectCodes":["MATH","PHY"],
                "buckets":[{"codes":["G12"],"percent":50},{"codes":["G10","G11"],"percent":50}]}],
                "parts":[{"id":"p","questionCount":4,"codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                {"id":"s1","subjectCode":"MATH","questionCount":2,"blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                {"id":"s2","subjectCode":"PHY","questionCount":2,"blocks":[{"id":"b2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        var g1 = new GroupQuota("b1", GroupQuota.GroupKind.SINGLE, 2, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        var g2 = new GroupQuota("b2", GroupQuota.GroupKind.SINGLE, 2, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        var s1 = new SectionOnPath("MATH", "s1", 2, null, List.of(g1));
        var s2 = new SectionOnPath("PHY", "s2", 2, null, List.of(g2));
        var witness = new SubjectWitness("MATH", 2, List.of(new GroupWitness("b1", List.of(pick("q1"), pick("q2")))));
        var witness2 = new SubjectWitness("PHY", 2, List.of(new GroupWitness("b2", List.of(pick("q3"), pick("q4")))));
        var result = new PathResult(SolvePath.of(0, List.of(s1, s2)), SolveStatus.VALID, null, null, null,
                List.of(witness, witness2), List.of(), null);
        Map<String, QuestionSourcePort.Candidate> candidates = Map.of(
                "q1:1", candidate("q1", "G12"), "q2:1", candidate("q2", "G12"),
                "q3:1", candidate("q3", "G10"), "q4:1", candidate("q4", "G10"));
        var units = ExamCheckDistribution.pickedUnits(result, candidates);
        var evidence = new ExamCheckDistribution.PathEvidence(result, "p:CA2", List.of("CA2"), units);
        var report = ExamCheckDistribution.analyze(document, evidence, mapper);
        assertThat(codes(report)).isEmpty();
    }

    @Test
    void examQuotaDeviationFlagsOffBucketOnly() throws Exception {
        var document = document("""
                {"examQuotas":[{"id":"q1","dimension":"GRADE","basis":"QUESTION","enforcement":"REFERENCE",
                "buckets":[{"codes":["G12"],"count":2},{"codes":["G10"],"count":0}]}],
                "parts":[{"id":"p","questionCount":4,"sections":[{"id":"s","subjectCode":"MATH","questionCount":4,
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":4}]}]}]}
                """);
        var witness = new SubjectWitness("MATH", 4, List.of(
                new GroupWitness("b", List.of(pick("q1"), pick("q2"), pick("q3"), pick("q4")))));
        var result = new PathResult(SolvePath.of(0, List.of(section("MATH", "s", 4))),
                SolveStatus.VALID, null, null, null, List.of(witness), List.of(), null);
        Map<String, QuestionSourcePort.Candidate> candidates = Map.of(
                "q1:1", candidate("q1", "G12"), "q2:1", candidate("q2", "G12"),
                "q3:1", candidate("q3", "G10"), "q4:1", candidate("q4", "G10"));
        var units = ExamCheckDistribution.pickedUnits(result, candidates);
        var evidence = new ExamCheckDistribution.PathEvidence(result, "p:CA2", List.of("CA2"), units);
        var report = ExamCheckDistribution.analyze(document, evidence, mapper);
        assertThat(codes(report)).containsExactly("EXAM_QUOTA_DEVIATION");
        ObjectNode issue = report.issues().getFirst();
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G10");
        assertThat(issue.path("required").asInt()).isEqualTo(0);
        assertThat(issue.path("available").asInt()).isEqualTo(2);
        assertThat(issue.path("location").path("pathId").asText()).isEqualTo("p:CA2");
        assertThat(texts(issue, "affectedCodes")).containsExactly("CA2");
        assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
    }

    @Test
    void requiredExamQuotaIsNotComparedHere() throws Exception {
        var document = document("""
                {"examQuotas":[{"id":"q1","dimension":"GRADE","enforcement":"REQUIRED","subjectCodes":["MATH"],
                "buckets":[{"codes":["G12"],"count":9}]}],
                "parts":[{"id":"p","questionCount":2,"sections":[{"id":"s","subjectCode":"MATH","questionCount":2,
                "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        var path = path("MATH", "s", 2, List.of(pick("q1"), pick("q2")));
        var report = analyze(document, path, List.of());
        assertThat(codes(report)).isEmpty();
    }

    @Test
    void topicQuotaInfeasibleCarriesQuotaCountsAndLocation() throws Exception {
        var rules = rules();
        var location = new ExamCheckRules.EvidenceLocation("p", "s", "MATH", "b", 4, 2, "path1", List.of("QD01"));
        var facts = List.of(new ExamCheckRules.TopicQuotaEvidence(location, "tq-1", 5, 2),
                new ExamCheckRules.TopicQuotaEvidence(location, "tq-2", 2, 2));
        List<ObjectNode> issues = rules.topicQuotaIssues(facts);
        assertThat(issues).hasSize(1);
        ObjectNode issue = issues.getFirst();
        assertThat(issue.path("code").asText()).isEqualTo("TOPIC_QUOTA_INFEASIBLE");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("required").asInt()).isEqualTo(5);
        assertThat(issue.path("available").asInt()).isEqualTo(2);
        assertThat(issue.path("location").path("slotIndex").asInt()).isEqualTo(4);
        assertThat(issue.path("key").asText()).contains("tq-1");
    }

    @Test
    void combinationListChangeWarnsOnlyOnContentDifferences() throws Exception {
        var rules = rules();
        var served = List.of(
                new ExamCheckRules.CombinationContent("QD01", List.of("PHY", "CHEM"), null),
                new ExamCheckRules.CombinationContent("QD02", List.of("CHEM", "BIO"), null));
        var same = rules.combinationListIssues(served, served);
        assertThat(same).isEmpty();
        var current = List.of(
                new ExamCheckRules.CombinationContent("QD01", List.of("PHY", "BIO"), null),
                new ExamCheckRules.CombinationContent("QD03", List.of("CHEM", "BIO"), null));
        List<ObjectNode> issues = rules.combinationListIssues(served, current);
        assertThat(issues).hasSize(1);
        ObjectNode issue = issues.getFirst();
        assertThat(issue.path("code").asText()).isEqualTo("COMBINATION_LIST_CHANGED");
        assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
        assertThat(texts(issue, "affectedCodes")).containsExactlyInAnyOrder("QD01", "QD02", "QD03");
        var branchChanged = rules.combinationListIssues(served,
                List.of(new ExamCheckRules.CombinationContent("QD01", List.of("PHY", "CHEM"), "B"),
                        new ExamCheckRules.CombinationContent("QD02", List.of("CHEM", "BIO"), null)));
        assertThat(branchChanged).hasSize(1);
    }

    private static List<String> texts(ObjectNode issue, String field) {
        return StreamSupport.stream(issue.path(field).spliterator(), false).map(n -> n.asText()).toList();
    }

    private ExamCheckRules rules() throws Exception {
        Exam exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", null, "FULL", "SHARED", 60,
                null, "RAW", Instant.now(), UUID.randomUUID());
        return new ExamCheckRules(mapper, exam, document("{\"parts\":[]}"));
    }

    private PathResult path(String subject, String sectionId, int count, List<QuestionPick> picks) {
        var witness = new SubjectWitness(subject, count, List.of(new GroupWitness("b", picks)));
        return new PathResult(SolvePath.of(0, List.of(section(subject, sectionId, count))),
                SolveStatus.VALID, null, null, null, List.of(witness), List.of(), null);
    }

    private static SectionOnPath section(String subject, String sectionId, int count) {
        var group = new GroupQuota("b", GroupQuota.GroupKind.SINGLE, count, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        return new SectionOnPath(subject, sectionId, count, null, List.of(group));
    }

    private ExamCheckDistribution.Report analyze(ExamStructureDocument document, PathResult result,
                                               List<String> codes) {
        Map<String, QuestionSourcePort.Candidate> candidates = new LinkedHashMap<>();
        for (SubjectWitness w : result.witness()) {
            for (GroupWitness g : w.groups()) {
                for (QuestionPick p : g.picks()) {
                    candidates.put(p.questionId() + ":" + p.versionNo(), candidate(p.questionId(), "G12"));
                }
            }
        }
        var units = ExamCheckDistribution.pickedUnits(result, candidates);
        return ExamCheckDistribution.analyze(document,
                new ExamCheckDistribution.PathEvidence(result, "default", codes, units), mapper);
    }

    private List<String> codes(ExamCheckDistribution.Report report) {
        return report.issues().stream().map(issue -> issue.path("code").asText()).toList();
    }

    private QuestionPick pick(String questionId) {
        return new QuestionPick(questionId, 1);
    }

    private QuestionSourcePort.Candidate candidate(String questionId, String grade) {
        UUID id = UUID.nameUUIDFromBytes(questionId.getBytes());
        var unit = new QuestionSourcePort.Unit(id, null, QuestionType.SINGLE_CHOICE, grade, "D",
                List.of(), List.of(), BigDecimal.ONE);
        return new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null);
    }

    private ExamStructureDocument document(String json) throws Exception {
        return mapper.readValue(json, ExamStructureDocument.class);
    }
}
