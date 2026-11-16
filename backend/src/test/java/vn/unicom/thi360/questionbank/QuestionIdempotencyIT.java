package vn.unicom.thi360.questionbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Idempotency của các lệnh ghi câu hỏi (spec 7.1): {@code Idempotency-Key} bắt buộc trên
 * tạo/nhân đôi/xóa/loạt; cùng khóa + cùng thân trả lại kết quả đã lưu (không chạy lại nghiệp
 * vụ), cùng khóa + khác thân → 409 {@code IDEMPOTENCY_KEY_REUSED}.
 */
class QuestionIdempotencyIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        QuestionTestSupport.deleteAllQuestions(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM shared.idempotency_key WHERE scope LIKE '%:question.%'");
    }

    @Test
    void missingKeyRejected400() throws Exception {
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void createReplayReturnsStoredResultWithoutSecondInsert() throws Exception {
        String key = "k-replay-" + UUID.randomUUID();
        String request = QuestionTestSupport.createSingleChoiceRequest();
        MvcResult first = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        String code = read(first).at("/summary/code").asText();

        MvcResult replay = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        // Cùng nội dung đã lưu; không câu thứ hai được tạo.
        org.assertj.core.api.Assertions.assertThat(
                        read(replay).at("/summary/code").asText())
                .isEqualTo(code);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question WHERE parent_id IS NULL", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyRejected409() throws Exception {
        String key = "k-reuse-" + UUID.randomUUID();
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createQuestionSetRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void bulkReadyReplayDoesNotDoubleApply() throws Exception {
        MvcResult created = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-c1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(read(created).at("/summary/id").asText());

        String key = "k-bulk-" + UUID.randomUUID();
        String body = "{\"ids\":[\"" + id + "\"]}";
        mockMvc.perform(post(BASE + "/bulk/ready")
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", org.hamcrest.Matchers.hasSize(1)));
        assertSourceRevision(1L);

        // Phát lại cùng lệnh: kết quả đã lưu trả lại, revision nguồn không tăng thêm.
        mockMvc.perform(post(BASE + "/bulk/ready")
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated", org.hamcrest.Matchers.hasSize(1)));
        assertSourceRevision(1L);
    }

    @Test
    void deleteReplayIsIdempotent() throws Exception {
        MvcResult created = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-c2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(read(created).at("/summary/id").asText());
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);

        String key = "k-delete-" + UUID.randomUUID();
        mockMvc.perform(delete(BASE + "/{id}", id)
                        .param("expectedRevision", String.valueOf(revision))
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key))
                .andExpect(status().isNoContent());
        // Phát lại lệnh xóa: trả lại kết quả đã lưu (204) thay vì 404/412.
        mockMvc.perform(delete(BASE + "/{id}", id)
                        .param("expectedRevision", String.valueOf(revision))
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", key))
                .andExpect(status().isNoContent());
    }

    private void assertSourceRevision(long expected) {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM questionbank.question_source_revision WHERE subject_item_id = ?",
                Long.class, QuestionTestSupport.SUBJECT_TOAN_ID);
        org.assertj.core.api.Assertions.assertThat(revision).isEqualTo(expected);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
