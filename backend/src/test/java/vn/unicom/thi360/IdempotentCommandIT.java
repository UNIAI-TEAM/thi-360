package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-7 (UNI-2607): quy ước {@code IdempotentCommand} trên endpoint thử
 * {@code POST /api/v1/admin/__test/idempotent}.
 *
 * <ul>
 *   <li>Thiếu {@code Idempotency-Key} → 400 {@code IDEMPOTENCY_KEY_REQUIRED};
 *   <li>Cùng khóa + cùng thân → replay trả đúng kết quả đã lưu (supplier không chạy lại);
 *   <li>Cùng khóa + khác thân → 409 {@code IDEMPOTENCY_KEY_REUSED};
 *   <li>Khác người dùng cùng khóa → phạm vi khác, chạy bình thường.
 * </ul>
 */
@Import(TestBe1Endpoints.class)
class IdempotentCommandIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/__test/idempotent";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanKeys() {
        jdbcTemplate.update(
                "DELETE FROM shared.idempotency_key WHERE scope LIKE '%:test.idempotent%'");
    }

    @Test
    void missingKey_returns400_idempotencyKeyRequired() throws Exception {
        mockMvc.perform(post(URL)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void sameKeySameBody_replaysStoredResult_withoutReRunningSupplier() throws Exception {
        String key = "key-" + Ids.newId();
        String body = "{\"name\":\"replayed\"}";
        RequestPostProcessor user = asUser(Ids.newId(), "same-user", Permissions.QUESTION_MODIFY);

        MvcResult first = mockMvc.perform(post(URL)
                        .with(user)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        int firstRun = readRun(first);

        MvcResult replay = mockMvc.perform(post(URL)
                        .with(user)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readRun(replay)).isEqualTo(firstRun);
    }

    @Test
    void sameKeyDifferentBody_returns409_idempotencyKeyReused() throws Exception {
        String key = "key-" + Ids.newId();
        RequestPostProcessor user = asUser(Ids.newId(), "conflict-user", Permissions.QUESTION_MODIFY);

        mockMvc.perform(post(URL)
                        .with(user)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"v1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(URL)
                        .with(user)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"v2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void sameKeyDifferentUser_isDifferentScope_andRunsAgain() throws Exception {
        String key = "key-" + Ids.newId();
        String body = "{\"name\":\"scoped\"}";

        MvcResult first = mockMvc.perform(post(URL)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult secondUser = mockMvc.perform(post(URL)
                        .with(asUser(Ids.newId(), "other-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        // Người khác cùng khóa = phạm vi khác: lệnh chạy thật, run tăng
        assertThat(readRun(secondUser)).isGreaterThan(readRun(first));
    }

    @Test
    void permissionCheckRunsBeforeIdempotency_forbiddenStill403() throws Exception {
        mockMvc.perform(post(URL)
                        .with(asUser(Permissions.USER_VIEW))
                        .header("Idempotency-Key", "key-" + Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private int readRun(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("run").asInt();
    }

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
}
