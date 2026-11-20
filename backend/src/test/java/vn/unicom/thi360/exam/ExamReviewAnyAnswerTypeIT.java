package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    @Autowired private vn.unicom.thi360.exam.application.ExamPreviewStore previewStore;

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Test
    void reviewExamWithAnyAnswerTypeHasNoIssueAndStillGeneratesThePaper() throws Exception {
        // An isolated pool that cannot reach 10 with single choice alone (4), so a regression that resolves the omitted
        // type to SINGLE_CHOICE fails; the eight short-answer questions are needed to fill the paper.
        isolateToanPool(4, 8);
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
        assertDrawnPaper(trial);
    }

    /** UNI-2752 R1-1: a published review version keeps its build mode for the target preview of that version. */
    @Test
    void publishedReviewVersionStillGeneratesThroughTheVersionPreview() throws Exception {
        isolateToanPool(4, 8);
        String id = createReview();
        JsonNode check = check(id);
        assertThat(check.path("status").asText()).as("check %s", check.path("groups").findValuesAsText("code"))
                .isEqualTo("VALID");
        publish(id, check);
        assertThat(jdbc.queryForObject("SELECT config->>'buildMode' FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                String.class, UUID.fromString(id))).isEqualTo("REVIEW");

        JsonNode trial = pathPreview(id);
        assertThat(trial.path("status").asText()).as("version trial %s", trial).isEqualTo("GENERATED");
        assertDrawnPaper(trial);
    }

    /** A version published before the mode was stored has no buildMode and is read as STANDARD, as it always was. */
    @Test
    void versionSnapshotWithoutAStoredModeIsStandard() throws Exception {
        isolateToanPool(4, 8);
        String id = createReview();
        publish(id, check(id));
        assertThat(previewStore.loadVersion(UUID.fromString(id), 1).exam().getBuildMode()).isEqualTo("REVIEW");
        assertThat(jdbc.update("UPDATE exam.exam_version SET config = config - 'buildMode' WHERE exam_id = ? AND version_no = 1",
                UUID.fromString(id))).isOne();

        assertThat(previewStore.loadVersion(UUID.fromString(id), 1).exam().getBuildMode()).isEqualTo("STANDARD");
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

    /** Retires the seeded Toán stock in this transaction and seeds exactly the given questions of each type. */
    private void isolateToanPool(int singleChoice, int shortAnswer) {
        entityManager.flush();
        jdbc.update("UPDATE questionbank.question SET status = 'RETIRED' WHERE subject_item_id = ? AND parent_id IS NULL "
                + "AND status = 'READY'", TOAN);
        for (int i = 0; i < singleChoice; i++) seedQuestion("SINGLE_CHOICE");
        for (int i = 0; i < shortAnswer; i++) seedQuestion("SHORT_ANSWER");
        entityManager.clear();
    }

    /** Exactly COUNT questions, each drawn once, and the short-answer type the single-choice stock cannot replace. */
    private static void assertDrawnPaper(JsonNode trial) {
        List<String> ids = new ArrayList<>();
        Set<String> types = new HashSet<>();
        for (JsonNode part : trial.path("paper").path("parts")) {
            for (JsonNode block : part.path("blocks")) {
                for (JsonNode item : block.path("items")) {
                    ids.add(item.path("question").path("questionId").asText());
                    types.add(item.path("question").path("type").asText());
                }
            }
        }
        assertThat(ids).as("drawn question IDs %s", ids).hasSize(COUNT);
        assertThat(new HashSet<>(ids)).as("no duplicates in %s", ids).hasSize(COUNT);
        assertThat(types).as("the paper needs a second standalone type").contains("SHORT_ANSWER");
    }

    private void publish(String id, JsonNode check) throws Exception {
        entityManager.flush();
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, UUID.fromString(id));
        String path = check.path("paths").get(0).path("pathId").asText();
        ExamListingFixture.fill(jdbc, UUID.fromString(id));
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", id)
                        .with(asUser(UUID.randomUUID(), "publisher", Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathIds\":[\"" + path + "\"],\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        entityManager.clear();
    }

    /** A preview of the published version 1 through the default path. */
    private JsonNode pathPreview(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":3,\"version\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
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

    private void seedQuestion(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,?,'READY',?,1,1)", id, "RA-" + id, type, TOAN);
        String body = "SHORT_ANSWER".equals(type)
                ? "{\"type\":\"SHORT_ANSWER\",\"answers\":[\"42\"]}"
                : "{\"type\":\"SINGLE_CHOICE\",\"options\":["
                        + "{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}";
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{\"content\":\"q\",\"points\":1,\"body\":" + body + "}");
    }
}
