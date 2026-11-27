package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class AccountSelfDeletionConcurrencyIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KeycloakIdentityClient keycloakClient;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<String> usedIps = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID userId : createdUserIds) {
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redis.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
        for (String ip : usedIps) {
            redis.delete(RateLimitFilter.KEY_PREFIX + ip);
        }
        usedIps.clear();
    }

    @Test
    void twentyConcurrentDeletePairsExecuteExactlyOnceWithout400Or5xx() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD));
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int iter = 0; iter < 20; iter++) {
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                String username = "delconc." + iter + "." + suffix;
                String email = username + "@thi360-test.example";
                UUID userId = createStudent(username, email);

                String ip = SelfRegistrationSupport.freshIp();
                usedIps.add(ip);
                MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                                .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "identifier", username, "password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
                Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
                Cookie xsrf = login.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Integer> call = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/v1/me/account/deletion")
                                    .cookie(access, xsrf)
                                    .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                };

                Future<Integer> f1 = pool.submit(call);
                Future<Integer> f2 = pool.submit(call);
                int s1 = f1.get(30, TimeUnit.SECONDS);
                int s2 = f2.get(30, TimeUnit.SECONDS);

                List<Integer> statuses = List.of(s1, s2);
                assertThat(statuses)
                        .as("iteration %d statuses must only be 204 or 401", iter)
                        .allMatch(s -> s == 204 || s == 401);
                assertThat(statuses)
                        .as("iteration %d must have at least one 204", iter)
                        .contains(204);

                assertThat(jdbc.queryForObject(
                        "SELECT deleted_at FROM identity.user_account WHERE id = ?", Object.class, userId))
                        .isNotNull();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                        Integer.class, userId)).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM shared.outbox_event
                        WHERE topic = 'audit' AND payload->>'action' = 'USER_SELF_DELETED' AND payload->>'targetId' = ?
                        """, Integer.class, userId.toString())).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM shared.outbox_event
                        WHERE topic = 'identity.user.deleted' AND payload->>'userId' = ?
                        """, Integer.class, userId.toString())).isEqualTo(1);
            }
        }
    }

    private UUID createStudent(String username, String email) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Conc", "Student", PASSWORD, true, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'Conc Student', ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username.toLowerCase(),
                email, email.toLowerCase(), now, now, userId, now, userId);
        createdUserIds.add(userId);
        return userId;
    }
}
