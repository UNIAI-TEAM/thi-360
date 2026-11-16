package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamIdempotencyIT extends AbstractIntegrationTest {
    @Autowired
    private ObjectMapper mapper;

    @Test
    void sameKeyReplaysAndDifferentBodyConflicts() throws Exception {
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"HSA-F-"
                + UUID.randomUUID().toString().substring(0, 8) + "\"}";
        String first = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(actor, "creator", Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(actor, "creator", Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(replay).path("id").asText())
                .isEqualTo(mapper.readTree(first).path("id").asText());
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(actor, "creator", Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Draft", "Changed")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
