package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewIssue;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.QuotaRef;
import vn.unicom.thi360.exam.domain.solver.SolveReason;

/**
 * UNI-2758: a shortage that comes from a MAPID rule names the rule (ruleId, the node ids of the rule) so the web
 * can scroll to {@code mapid-rule-<ruleId>}; a PRESENT shortage names the MAPID itself.
 */
class MapidShortageNamingTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    private ExamStructureDocument document(String mode) throws Exception {
        return mapper.readValue("""
                {"parts":[{"id":"p","name":"Toán","sections":[{"id":"s","subjectCode":"MATH","questionCount":4,
                  "mapidMode":"%s","mapidRules":[
                    {"id":"exactN0","type":"COUNT","comparator":"EXACT","count":2,"nodeIds":["%s"]},
                    {"id":"present","type":"PRESENT","groupId":"g1","nodeIds":["%s","%s"]}],
                  "blocks":[{"id":"g1","name":"Trắc nghiệm","questionType":"SINGLE_CHOICE","questionCount":4}]}]}]}
                """.formatted(mode, A, A, B), ExamStructureDocument.class);
    }

    @Test
    void aCapFailureNamesTheRuleAndItsNodes() throws Exception {
        var issues = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document("MATRIX"), 30);
        var issue = issues.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "TOPIC_CAP", "exactN0"), List.of());
        assertThat(issue.path("code").asText()).isEqualTo("SUBJECT_QUOTA_INFEASIBLE");
        assertThat(issue.path("ruleId").asText()).isEqualTo("exactN0");
        assertThat(texts(issue.path("nodeIds"))).containsExactly(A.toString());
        assertThat(issue.path("sectionId").asText()).isEqualTo("s");
    }

    @Test
    void aCompiledTopicQuotaThatIsARuleIdNamesTheRule() throws Exception {
        var issues = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document("MATRIX"), 30);
        var issue = issues.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "TOPIC", "present"), List.of());
        assertThat(issue.path("ruleId").asText()).isEqualTo("present");
        assertThat(texts(issue.path("nodeIds"))).containsExactly(A.toString(), B.toString());
    }

    @Test
    void otherQuotasAndLegacySectionsKeepNoRuleId() throws Exception {
        var matrix = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document("MATRIX"), 30);
        assertThat(matrix.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "GRADE", "G12"), List.of()).hasNonNull("ruleId")).isFalse();
        assertThat(matrix.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "TOPIC", "unknown"), List.of()).hasNonNull("ruleId")).isFalse();
        // ALL compiles nothing: a quota id equal to a rule id is not a rule there.
        var all = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document("ALL"), 30);
        assertThat(all.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "TOPIC", "present"), List.of()).hasNonNull("ruleId")).isFalse();
    }

    @Test
    void thePreviewFailureCarriesTheRuleIdToo() throws Exception {
        var issues = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), document("MATRIX"), 30);
        ObjectNode issue = issues.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "g1",
                new QuotaRef("MATH", "s", "TOPIC_CAP", "exactN0"), List.of());
        // The application mapper ignores unknown properties; the check issue carries more fields than a preview one.
        var lenient = mapper.copy().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode preview = lenient.valueToTree(lenient.convertValue(issue, PreviewIssue.class));
        assertThat(preview.path("ruleId").asText()).isEqualTo("exactN0");
        assertThat(texts(preview.path("nodeIds"))).containsExactly(A.toString());
    }

    @Test
    void aPresentShortageNamesTheRuleAndTheMapid() throws Exception {
        var rules = rules(document("MATRIX"));
        var location = new ExamCheckRules.EvidenceLocation("p", "s", "MATH", "g1", null, null, null, List.of());
        var found = rules.topicQuotaIssues(List.of(new ExamCheckRules.TopicQuotaEvidence(location, "present", 1, 0)));
        assertThat(found).singleElement().satisfies(issue -> {
            assertThat(issue.path("code").asText()).isEqualTo("TOPIC_QUOTA_INFEASIBLE");
            assertThat(issue.path("ruleId").asText()).isEqualTo("present");
            assertThat(texts(issue.path("nodeIds"))).containsExactly(A.toString(), B.toString());
            assertThat(issue.path("required").asInt()).isEqualTo(1);
            assertThat(issue.path("available").asInt()).isZero();
        });
        // A legacy topic quota (no MAPID mode) keeps today's issue without a rule.
        var legacy = rules(document("ALL")).topicQuotaIssues(List.of(
                new ExamCheckRules.TopicQuotaEvidence(location, "present", 1, 0)));
        assertThat(legacy).singleElement().satisfies(issue -> assertThat(issue.hasNonNull("ruleId")).isFalse());
    }

    private ExamCheckRules rules(ExamStructureDocument document) {
        var exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", null, "FULL", "SHARED", 60, null, "RAW",
                Instant.parse("2026-11-20T00:00:00Z"), UUID.randomUUID());
        return new ExamCheckRules(mapper, exam, document, Map.of(), Map.of());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
