package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

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

    @Autowired
    private IdempotentCommand idempotentCommand;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void createProbeTable() {
        // Bảng fixture cho test atomicity: DDL auto-commit, tồn tại ngoài transaction test.
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS shared.be1_probe_row (id text PRIMARY KEY, note text NOT NULL)");
    }

    @AfterEach
    void cleanKeys() {
        jdbcTemplate.update(
                "DELETE FROM shared.idempotency_key WHERE scope LIKE '%:test.idempotent%'"
                        + " OR scope LIKE '%:test.atomicity%'");
        jdbcTemplate.update("DELETE FROM shared.be1_probe_row");
        SecurityContextHolder.clearContext();
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

    @Test
    void rolledBackTransactionDropsBusinessChangeAndStoredResult_retryRunsOnce() {
        // R1-5: nghiệp vụ của supplier và bản ghi idempotency phải cùng một transaction.
        // Buộc transaction bọc ngoài rollback rồi kiểm cả dòng nghiệp vụ lẫn khóa đều mất;
        // gửi lại cùng khóa phải chạy lại đúng một lần và replay được.
        UUID userId = Ids.newId();
        authenticateAs(userId, "atomic-user");

        String key = "key-" + Ids.newId();
        String probeId = "p-" + Ids.newId();
        Map<String, Object> body = Map.of("name", "atomic");
        AtomicInteger runs = new AtomicInteger();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Map<String, Object> result = idempotentCommand.execute(
                    key, "test.atomicity", body, Map.class,
                    () -> {
                        runs.incrementAndGet();
                        jdbcTemplate.update(
                                "INSERT INTO shared.be1_probe_row (id, note) VALUES (?, ?)",
                                probeId, "ran");
                        return Map.of("probeId", probeId, "run", runs.get());
                    });
            assertThat(result.get("probeId")).isEqualTo(probeId);
            status.setRollbackOnly();
        });

        // Cả hai mặt của lệnh đều lùi theo transaction: không dòng nghiệp vụ, không khóa.
        assertThat(countProbeRows(probeId)).isZero();
        assertThat(countKeys(key)).isZero();

        Map<String, Object> first = idempotentCommand.execute(
                key, "test.atomicity", body, Map.class,
                () -> {
                    runs.incrementAndGet();
                    jdbcTemplate.update(
                            "INSERT INTO shared.be1_probe_row (id, note) VALUES (?, ?)",
                            probeId, "ran");
                    return Map.of("probeId", probeId, "run", runs.get());
                });
        assertThat(first.get("probeId")).isEqualTo(probeId);
        assertThat(runs.get()).isEqualTo(2);
        assertThat(countProbeRows(probeId)).isEqualTo(1);
        assertThat(countKeys(key)).isEqualTo(1);

        Map<String, Object> replay = idempotentCommand.execute(
                key, "test.atomicity", body, Map.class,
                () -> {
                    runs.incrementAndGet();
                    throw new AssertionError("replay must not re-run the supplier");
                });
        assertThat(replay.get("probeId")).isEqualTo(probeId);
        assertThat(runs.get()).isEqualTo(2);
    }

    private void authenticateAs(UUID userId, String username) {
        CurrentUser user = new CurrentUser(
                userId, username, Realm.APP, Access.MANAGE,
                PermissionSet.ofCodes(List.of(Permissions.QUESTION_MODIFY)));
        SecurityContextHolder.getContext()
                .setAuthentication(new CurrentUserAuthenticationToken(user, "test-sid"));
    }

    private int countProbeRows(String probeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.be1_probe_row WHERE id = ?",
                Integer.class,
                probeId);
        return count == null ? 0 : count;
    }

    private int countKeys(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.idempotency_key WHERE \"key\" = ?",
                Integer.class,
                key);
        return count == null ? 0 : count;
    }

    private int readRun(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("run").asInt();
    }

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
}
