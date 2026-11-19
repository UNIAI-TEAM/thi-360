package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2752 (AD-17 Q1): a review exam's block "Bất kỳ" (questionType null, no answer quotas) means any type, so the
 * check must not report GROUP_ANSWER_TYPE_MISSING and "sinh thử" must still draw the requested count. A standard
 * exam keeps the rule.
 */
@Transactional
class ExamReviewAnyAnswerTypeIT extends AbstractIntegrationTest {
    private static final UUID TOAN = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final String RULE = "GROUP_ANSWER_TYPE_MISSING";
    private static final int COUNT = 10;

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Test
    void reviewExamWithAnyAnswerTypeHasNoIssueAndStillGeneratesThePaper() throws Exception {
        for (int i = 0; i < 30; i++) {
            seedQuestion();
        }
        String id = createReview();
        JsonNode structure = structure(id);
        JsonNode block = structure.path("parts").get(0).path("sections").get(0).path("blocks").get(0);
        assertThat(block.path("questionType").isNull() || block.path("questionType").isMissingNode())
                .as("the review template block is \"Bất kỳ\"").isTrue();
        assertThat(structure.path("parts").get(0).path("sections").get(0).path("answerQuotas").isNull()
                || structure.path("parts").get(0).path("sections").get(0).path("answerQuotas").isMissingNode()).isTrue();

        JsonNode check = check(id);
        assertThat(check.path("groups").findValuesAsText("code")).doesNotContain(RULE);
        assertThat(check.path("status").asText()).as("check %s", check.path("groups").findValuesAsText("code"))
                .isEqualTo("VALID");

        JsonNode trial = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":3}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(trial.path("status").asText()).as("trial %s", trial).isEqualTo("GENERATED");
        Set<String> drawn = new HashSet<>(trial.path("paper").findValuesAsText("questionId"));
        assertThat(drawn).hasSize(COUNT);
    }

    @Test
    void standardExamWithAnyAnswerTypeStillReportsTheMissingAnswerType() throws Exception {
        String id = createReview();
        // Same "Bất kỳ" structure, but the exam is a standard one: the rule applies as before.
        entityManager.flush();
        assertThat(jdbc.update("UPDATE exam.exam SET build_mode = 'STANDARD' WHERE id = ?", UUID.fromString(id))).isOne();
        entityManager.clear();
        JsonNode check = check(id);
        assertThat(check.path("groups").findValuesAsText("code")).as("check %s", check).contains(RULE);
    }

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Step 1 of the review screen: kind, subject, duration and the question count of the template part. */
    private String createReview() throws Exception {
        String partId = jdbc.queryForObject(
                "SELECT structure->'parts'->0->>'id' FROM exam.exam_template WHERE code = 'HSA_REVIEW'", String.class);
        ObjectNode body = mapper.createObjectNode().put("kind", "HSA").put("scope", "SUBJECT")
                .put("subjectCode", "TOAN").put("buildMode", "REVIEW").put("name", "Review any type")
                .put("code", "R-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 45);
        body.putArray("overview").addObject().put("partId", partId).put("questionCount", COUNT)
                .put("durationMinutes", 45);
        JsonNode created = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return created.path("id").asText();
    }

    private void seedQuestion() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,1)", id, "RA-" + id, TOAN);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{\"content\":\"q\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":["
                        + "{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}}");
    }
}
