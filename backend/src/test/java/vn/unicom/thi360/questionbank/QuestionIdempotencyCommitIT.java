package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** Each request commits independently; no test transaction can hide a replay failure. */
class QuestionIdempotencyCommitIT extends AbstractIntegrationTest {
    private final UUID actor = UUID.randomUUID();
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @AfterEach
    void removeOnlyFixture() {
        String ids = "SELECT id FROM questionbank.question WHERE created_by = ?";
        jdbc.update("DELETE FROM questionbank.question_knowledge WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_tag WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_version WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_source_code_claim WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question WHERE created_by = ? AND parent_id IS NOT NULL", actor);
        jdbc.update("DELETE FROM questionbank.question WHERE created_by = ? AND parent_id IS NULL", actor);
        jdbc.update("DELETE FROM shared.idempotency_key WHERE scope LIKE ?", actor + ":question.%");
    }

    @Test
    void replayAndConflictAcrossCommittedRequests() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = QuestionTestSupport.createSingleChoiceRequest();
        var user = asUser(actor, "committed-replay", Permissions.QUESTION_MODIFY);
        var first = mockMvc.perform(post("/api/v1/admin/questions").with(user)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        var replay = mockMvc.perform(post("/api/v1/admin/questions").with(user)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(mapper.readTree(replay.getResponse().getContentAsString()).at("/summary/id"))
                .isEqualTo(mapper.readTree(first.getResponse().getContentAsString()).at("/summary/id"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE created_by = ?", Integer.class, actor))
                .isEqualTo(1);
        mockMvc.perform(post("/api/v1/admin/questions").with(user)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createQuestionSetRequest()))
                .andExpect(status().isConflict());
    }
    @Test
    void deleteSameKeyWithDifferentRevisionConflictsAcrossCommits() throws Exception {
        var user = asUser(actor, "committed-delete", Permissions.QUESTION_MODIFY);
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(user)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).at("/summary/id").asText());
        long revision = jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
        String key = UUID.randomUUID().toString();
        mockMvc.perform(delete("/api/v1/admin/questions/{id}", id).with(user)
                        .header("Idempotency-Key", key).param("expectedRevision", Long.toString(revision)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/questions/{id}", id).with(user)
                        .header("Idempotency-Key", key).param("expectedRevision", Long.toString(revision + 1)))
                .andExpect(status().isConflict());
    }
}