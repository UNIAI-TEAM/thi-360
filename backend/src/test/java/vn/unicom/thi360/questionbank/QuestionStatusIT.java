package vn.unicom.thi360.questionbank;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Vòng đời trạng thái câu hỏi (spec 4.4, 4.5): Nháp → Sẵn sàng → Ngừng, kiểm đủ điều kiện
 * (422 {@code QUESTION_NOT_READY}), đổi trạng thái hàng loạt với câu bị bỏ qua, và revision
 * nguồn theo môn tăng khi tập Sẵn sàng đổi (D14).
 */
class QuestionStatusIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        QuestionTestSupport.deleteAllQuestions(jdbcTemplate);
    }

    @Test
    void draftBecomesReadyThenRetires() throws Exception {
        UUID id = createQuestion();
        long revision = currentRevision(id);

        // Ready: đóng bản làm việc, currentVersionNo = 1, revision nguồn Toán = 1.
        mockMvc.perform(post(BASE + "/{id}/ready", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.version").value(1));
        assertSourceRevision(QuestionTestSupport.SUBJECT_TOAN_ID, 1L);

        // Ready lần hai → 409 QUESTION_ALREADY_READY.
        mockMvc.perform(post(BASE + "/{id}/ready", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_ALREADY_READY"));

        // Retire: trạng thái Ngừng, phiên bản phục vụ giữ nguyên, revision nguồn tăng lên 2.
        mockMvc.perform(post(BASE + "/{id}/retire", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.version").value(1));
        assertSourceRevision(QuestionTestSupport.SUBJECT_TOAN_ID, 2L);

        // Retire lần hai → 409 QUESTION_ALREADY_RETIRED; câu Ngừng không ready được.
        mockMvc.perform(post(BASE + "/{id}/retire", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_ALREADY_RETIRED"));
        mockMvc.perform(post(BASE + "/{id}/ready", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_ALREADY_RETIRED"));
    }

    @Test
    void readinessReportsMissingClassification() throws Exception {
        // Câu thiếu môn/khối/độ khó: readiness trả lỗi, ready bị chặn 422.
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-noclass-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p>Q?</p>\",\"body\":"
                                + QuestionTestSupport.singleChoiceBody() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(read(result).at("/summary/id").asText());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(BASE + "/{id}/readiness", id)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.errors", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.warnings").isArray());

        mockMvc.perform(post(BASE + "/{id}/ready", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_READY"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(3))));
    }

    @Test
    void readyPropagatesToChildrenAndBumpsOnce() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-setready-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createQuestionSetRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID parentId = UUID.fromString(read(result).at("/summary/id").asText());

        mockMvc.perform(post(BASE + "/{id}/ready", parentId)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(parentId) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // Câu con kế thừa READY + cùng version_no.
        Integer readyChildren = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question"
                        + " WHERE parent_id = ? AND status = 'READY' AND current_version_no = 1",
                Integer.class, parentId);
        org.assertj.core.api.Assertions.assertThat(readyChildren).isEqualTo(2);
        // Một lần tăng revision nguồn cho cả bộ (không tăng theo câu con).
        assertSourceRevision(QuestionTestSupport.SUBJECT_TOAN_ID, 1L);
    }

    @Test
    void bulkReadyUpdatesEligibleAndSkipsRest() throws Exception {
        UUID ready1 = createQuestion();
        UUID ready2 = createQuestion();
        // Câu không đủ điều kiện: thiếu phân loại.
        MvcResult incomplete = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-incomplete-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE_CHOICE\",\"body\":"
                                + QuestionTestSupport.singleChoiceBody() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID incompleteId = UUID.fromString(read(incomplete).at("/summary/id").asText());
        UUID missing = UUID.randomUUID();

        MvcResult result = mockMvc.perform(post(BASE + "/bulk/ready")
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-bulkready-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"%s\",\"%s\",\"%s\",\"%s\"]}"
                                .formatted(ready1, ready2, incompleteId, missing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", hasSize(2)))
                .andExpect(jsonPath("$.skipped", hasSize(2)))
                .andReturn();
        JsonNode json = read(result);
        org.assertj.core.api.Assertions.assertThat(
                        json.at("/skipped/0/questionId").asText().equals(incompleteId.toString())
                                ? json.at("/skipped/0/reason").asText()
                                : json.at("/skipped/1/reason").asText())
                .isEqualTo("NOT_READY");
        assertSourceRevision(QuestionTestSupport.SUBJECT_TOAN_ID, 2L);
    }

    @Test
    void bulkRetireSkipsDraftsAndMissing() throws Exception {
        UUID draft = createQuestion();
        MvcResult result = mockMvc.perform(post(BASE + "/bulk/retire")
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-bulkretire-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"%s\",\"%s\"]}".formatted(draft, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", hasSize(0)))
                .andExpect(jsonPath("$.skipped", hasSize(2)))
                .andReturn();
        JsonNode json = read(result);
        java.util.Set<String> reasons = new java.util.HashSet<>();
        json.at("/skipped").forEach(s -> reasons.add(s.get("reason").asText()));
        org.assertj.core.api.Assertions.assertThat(reasons)
                .containsExactlyInAnyOrder("NOT_READY", "NOT_FOUND");
    }

    // ---------------------------------------------------------------- trợ giúp

    private UUID createQuestion() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-fix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).at("/summary/id").asText());
    }

    private long currentRevision(UUID id) {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
        return revision == null ? -1 : revision;
    }

    private void assertSourceRevision(UUID subjectItemId, long expected) {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM questionbank.question_source_revision WHERE subject_item_id = ?",
                Long.class, subjectItemId);
        org.assertj.core.api.Assertions.assertThat(revision).isEqualTo(expected);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
