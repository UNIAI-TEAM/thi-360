package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * {@code POST /auth/password/reset} (TM 5.4): AC-12 đến AC-21 của plan ID-2 với Keycloak, Redis và PostgreSQL thật.
 */
class PasswordResetIT extends AbstractKeycloakIntegrationTest {

    private static final String IP = "203.0.113.171";
    private static final String NEW_PASSWORD = "Brand#New2026";
    private static final String RESET = "/api/v1/auth/password/reset";

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private TokenPepperProperties pepper;
    @Autowired
    private KeycloakProperties keycloakProperties;
    @Autowired
    private PasswordResetService passwordResetService;
    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;
    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private PasswordResetTestSupport support;
    private org.keycloak.admin.client.Keycloak adminClient;

    @BeforeEach
    void setUp() {
        support = new PasswordResetTestSupport(keycloakClient, userAccountRepository, jdbc, redis, pepper.pepper());
        clearBuckets();
    }

    @AfterEach
    void cleanup() {
        if (adminClient != null) {
            adminClient.close();
            adminClient = null;
        }
        support.cleanup();
        clearBuckets();
    }

    private void clearBuckets() {
        redis.delete(List.of(
                RateLimitFilter.KEY_PREFIX + IP,
                RateLimitFilter.FORGOT_KEY_PREFIX + IP,
                RateLimitFilter.RESET_KEY_PREFIX + IP));
    }

    private MvcResult reset(String token, String password, Cookie... cookies) throws Exception {
        var request = post(RESET)
                .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult login(Account account, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + account.username() + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private String freshToken(Account account) {
        Instant now = Instant.now();
        return support.insertToken(account.id(), now.minusSeconds(5), now.plus(Duration.ofHours(1)), null);
    }

    private static long passwordResetAudits(AuditRecorder recorder, Account account) {
        return org.mockito.Mockito.mockingDetails(recorder).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("record"))
                .map(i -> (vn.unicom.thi360.audit.spi.AuditEvent) i.getArgument(0))
                .filter(e -> "PASSWORD_RESET".equals(e.action()) && account.id().toString().equals(e.targetId()))
                .count();
    }

    @Test
    void publicWithStaleCookies() throws Exception {
        Account account = support.eligible("reset.stale");
        String token = freshToken(account);
        Cookie staleAccess = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "expired.invalid.jwt");
        Cookie staleRefresh = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale-refresh-token");

        // Không có cookie nào: forgot gọi được.
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isAccepted());

        // Còn thi360_at hết hạn và thi360_rt cũ, không có XSRF: cả hai vẫn gọi được.
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .cookie(staleAccess, staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isAccepted());
        MvcResult result = reset(token, NEW_PASSWORD, staleAccess, staleRefresh);
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void resetChangesPassword() throws Exception {
        Account account = support.eligible("reset.ok");
        boolean mustChangeBefore = userAccountRepository.findById(account.id()).orElseThrow().isMustChangePassword();
        Instant verifiedBefore = userAccountRepository.findById(account.id()).orElseThrow().getEmailVerifiedAt();
        String token = freshToken(account);

        MvcResult result = reset(token, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).as("reset never signs the user in").isEmpty();
        assertThat(result.getResponse().getCookies()).isEmpty();
        assertThat(login(account, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        MvcResult old = login(account, account.password());
        assertThat(old.getResponse().getStatus()).isEqualTo(401);
        assertThat(support.unusedTokenRows(account.id())).isZero();
        assertThat(userAccountRepository.findById(account.id()).orElseThrow().isMustChangePassword())
                .as("must_change_password is kept (interim decision)").isEqualTo(mustChangeBefore);
        assertThat(userAccountRepository.findById(account.id()).orElseThrow().getEmailVerifiedAt())
                .isEqualTo(verifiedBefore);
        assertThat(passwordResetAudits(auditRecorder, account)).isEqualTo(1);
        assertThat(writeMarkRow(account).get("password_write_started_at")).isNotNull();
        assertThat(writeMarkRow(account).get("finalized_at")).as("finalised with the success audit").isNotNull();
        verify(auditRecorder).record(argThat(event -> "PASSWORD_RESET".equals(event.action())
                && account.id().toString().equals(event.targetId())
                && !String.valueOf(event).contains(NEW_PASSWORD)
                && !String.valueOf(event).contains(token)));
    }

    @Test
    void usedTokenRefused() throws Exception {
        Account account = support.eligible("reset.used");
        String token = freshToken(account);

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

        mockMvc.perform(post(RESET)
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"Other#Pass2026\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        assertThat(login(account, NEW_PASSWORD).getResponse().getStatus()).as("password not changed again").isEqualTo(200);
    }

    @Test
    void expiredTokenRefused() throws Exception {
        Account account = support.eligible("reset.expired");
        Instant now = Instant.now();
        String expired = support.insertToken(account.id(), now.minus(Duration.ofHours(1)), now.minusSeconds(1), null);
        String justInside = support.insertToken(account.id(), now.minusSeconds(30), now.plusSeconds(5), null);

        // Vừa hết hạn (1 giờ + 1 giây sau lúc tạo): 422 và không đổi mật khẩu.
        MvcResult late = reset(expired, NEW_PASSWORD);
        assertThat(late.getResponse().getStatus()).isEqualTo(422);
        assertThat(late.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(login(account, account.password()).getResponse().getStatus()).isEqualTo(200);

        // Còn vài giây: vẫn dùng được.
        assertThat(reset(justInside, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void newRequestInvalidatesOld() throws Exception {
        Account account = support.eligible("reset.newer");
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\"}"))
                .andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> support.mailbox().count(account.email()) == 1);
        String oldToken = support.tokenFromLatestMail(account.email());

        support.ageTokens(account.id(), 62);
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\"}"))
                .andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> support.mailbox().count(account.email()) == 2);
        String newToken = support.tokenFromLatestMail(account.email());
        assertThat(newToken).isNotEqualTo(oldToken);

        MvcResult old = reset(oldToken, NEW_PASSWORD);
        assertThat(old.getResponse().getStatus()).isEqualTo(422);
        assertThat(old.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(reset(newToken, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void concurrentResetSingleWinner() throws Exception {
        Account account = support.eligible("reset.race");
        String token = freshToken(account);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        try {
            Callable<Void> call = () -> {
                start.await();
                statuses.add(reset(token, NEW_PASSWORD).getResponse().getStatus());
                return null;
            };
            List<Future<Void>> futures = List.of(pool.submit(call), pool.submit(call));
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 422);
        verify(keycloakClient, times(1)).setPassword(eq(account.id()), anyString(), eq(false));
        assertThat(support.unusedTokenRows(account.id())).isZero();
    }

    @Test
    void revokesAllSessions() throws Exception {
        Account account = support.eligible("reset.sessions");
        MvcResult first = login(account, account.password());
        MvcResult second = login(account, account.password());
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        Cookie access = first.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie refresh = first.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie xsrf = first.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        Cookie access2 = second.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").cookie(access2)).andExpect(status().isOk());
        assertThat(redis.hasKey("user-sess:" + account.id())).isTrue();
        assertThat(keycloakSessions(account)).as("two Keycloak sessions before reset").hasSizeGreaterThanOrEqualTo(2);

        assertThat(reset(freshToken(account), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").cookie(access2)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue()))
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("user-sess:" + account.id())).as("Redis session key deleted").isFalse();
        assertThat(keycloakSessions(account)).as("Keycloak sessions are gone, not just the Redis gate").isEmpty();
    }

    @Test
    void lockedAccountStaysLocked() throws Exception {
        Account account = support.create("reset.locked", UserStatus.INACTIVE, true, false);
        String token = freshToken(account);

        MvcResult result = reset(token, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(support.unusedTokenRows(account.id())).as("token is cancelled").isZero();
        assertThat(userAccountRepository.findById(account.id()).orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(keycloakClient, never()).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(passwordResetAudits(auditRecorder, account)).isZero();
    }

    @Test
    void weakPasswordKeepsToken() throws Exception {
        Account account = support.eligible("reset.weak");
        String token = freshToken(account);

        mockMvc.perform(post(RESET)
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

        assertThat(support.unusedTokenRows(account.id())).as("token not consumed by a weak password").isEqualTo(1);
        verify(keycloakClient, never()).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void keycloakFailureKeepsToken() throws Exception {
        Account account = support.eligible("reset.kcdown");
        MvcResult session = login(account, account.password());
        Cookie access = session.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        String token = freshToken(account);
        doThrow(new KeycloakIdentityClient.PasswordWriteRejectedException(new RuntimeException("connection refused")))
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());

        MvcResult result = reset(token, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(support.unusedTokenRows(account.id())).as("definite rejection: token released").isEqualTo(1);
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        assertThat(passwordResetAudits(auditRecorder, account)).isZero();
        assertThat(login(account, account.password()).getResponse().getStatus()).as("old password still valid").isEqualTo(200);
        verify(keycloakClient, never()).logoutAllSessions(any());
        assertThat(writeMarkRow(account).get("password_write_started_at")).as("definite rejection clears the mark").isNull();
    }

    @Test
    void indeterminateKeycloakFailureConsumesToken() throws Exception {
        Account account = support.eligible("reset.unknown");
        String token = freshToken(account);
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("read timed out")))
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(502);

        // Không loại trừ được việc Keycloak đã nhận mật khẩu: mã không được cấp lại quyền.
        assertThat(support.unusedTokenRows(account.id())).as("token stays consumed").isZero();
        org.mockito.Mockito.doCallRealMethod().when(keycloakClient).setPassword(any(), anyString(), anyBoolean());
        MvcResult replay = reset(token, NEW_PASSWORD);
        assertThat(replay.getResponse().getStatus()).isEqualTo(422);
        assertThat(replay.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(passwordResetAudits(auditRecorder, account)).isZero();
    }

    @Test
    void failedRevocationAfterWriteKeepsTokenUsedAndAudits() throws Exception {
        Account account = support.eligible("reset.revoke");
        MvcResult session = login(account, account.password());
        Cookie access = session.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        String token = freshToken(account);
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("logout down")))
                .when(keycloakClient).logoutAllSessions(account.id());

        // Mật khẩu đã đổi nên kết quả vẫn là thành công; thu hồi phiên lỗi chỉ để lại dấu chờ thử lại.
        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

        MvcResult replay = reset(token, "Another#Pass2026");
        assertThat(replay.getResponse().getStatus()).as("token cannot be replayed").isEqualTo(422);
        assertThat(passwordResetAudits(auditRecorder, account)).as("success audit recorded").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?"
                                + " AND password_changed_at IS NOT NULL AND sessions_revoked_at IS NULL",
                        Integer.class,
                        account.id()))
                .as("revocation is pending")
                .isEqualTo(1);

        // Việc thử lại không ghi lại mật khẩu: chỉ thu hồi phiên.
        org.mockito.Mockito.doCallRealMethod().when(keycloakClient).logoutAllSessions(any());
        org.mockito.Mockito.clearInvocations(keycloakClient);
        assertThat(passwordResetService.retryPendingRevocations()).isEqualTo(1);

        verify(keycloakClient, never()).setPassword(any(), anyString(), anyBoolean());
        assertThat(keycloakSessions(account)).isEmpty();
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isUnauthorized());
        assertThat(passwordResetService.retryPendingRevocations()).as("nothing left to retry").isZero();
    }

    @Test
    void issuanceDuringRejectedWriteLeavesOnlyNewestLinkUsable() throws Exception {
        Account account = support.eligible("reset.fence");
        Instant now = Instant.now();
        // Quá cooldown để lần phát hành giữa chừng được phép.
        String oldToken = support.insertToken(account.id(), now.minusSeconds(120), now.plus(Duration.ofHours(1)), null);
        org.mockito.Mockito.doAnswer(invocation -> {
                    passwordResetService.issue(account.email(), IP);
                    throw new KeycloakIdentityClient.PasswordWriteRejectedException(new RuntimeException("refused"));
                })
                .doCallRealMethod()
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());

        assertThat(reset(oldToken, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(502);

        String newToken = support.tokenFromLatestMail(account.email());
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(support.unusedTokenRows(account.id())).as("only the newest link is usable").isEqualTo(1);
        MvcResult old = reset(oldToken, NEW_PASSWORD);
        assertThat(old.getResponse().getStatus()).isEqualTo(422);
        assertThat(old.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(reset(newToken, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void linkIsBoundToTheRecoveryContact() throws Exception {
        // Đổi sang địa chỉ khác (đã xác minh): liên kết gửi tới địa chỉ cũ hết hiệu lực.
        Account replaced = support.eligible("reset.contact.replaced");
        String replacedToken = freshToken(replaced);
        jdbc.update("UPDATE identity.user_account SET email = ?, email_normalized = ?, email_verified_at = now() WHERE id = ?",
                "new." + replaced.email(), "new." + replaced.email(), replaced.id());
        // Xóa địa chỉ.
        Account cleared = support.eligible("reset.contact.cleared");
        String clearedToken = freshToken(cleared);
        jdbc.update("UPDATE identity.user_account SET email = NULL, email_normalized = NULL, email_verified_at = NULL WHERE id = ?",
                cleared.id());
        // Đường quản trị: địa chỉ giữ nguyên nhưng bị bỏ xác minh.
        Account unverified = support.eligible("reset.contact.unverified");
        String unverifiedToken = freshToken(unverified);
        jdbc.update("UPDATE identity.user_account SET email_verified_at = NULL WHERE id = ?", unverified.id());

        for (var entry : java.util.Map.of(replaced, replacedToken, cleared, clearedToken, unverified, unverifiedToken)
                .entrySet()) {
            MvcResult result = reset(entry.getValue(), NEW_PASSWORD);
            assertThat(result.getResponse().getStatus()).isEqualTo(422);
            assertThat(result.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
            assertThat(support.unusedTokenRows(entry.getKey().id())).as("link is invalidated").isZero();
            verify(keycloakClient, never()).setPassword(eq(entry.getKey().id()), anyString(), anyBoolean());
            assertThat(login(entry.getKey(), entry.getKey().password()).getResponse().getStatus())
                    .as("password not changed").isEqualTo(200);
        }
    }

    @Test
    void reverifiedAddressInvalidatesOlderLink() throws Exception {
        Account account = support.eligible("reset.reverify");
        passwordResetService.issue(account.email(), IP);
        String oldToken = support.tokenFromLatestMail(account.email());
        // Cùng địa chỉ được xác minh lại sau khi đổi liên hệ (xác nhận mới đặt email_verified_at mới).
        jdbc.update("UPDATE identity.user_account SET email_verified_at = email_verified_at + interval '1 minute'"
                + " WHERE id = ?", account.id());

        MvcResult stale = reset(oldToken, NEW_PASSWORD);

        assertThat(stale.getResponse().getStatus()).isEqualTo(422);
        assertThat(stale.getResponse().getContentAsString()).contains("RESET_TOKEN_INVALID");
        assertThat(support.unusedTokenRows(account.id())).as("older link is invalidated").isZero();
        verify(keycloakClient, never()).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(login(account, account.password()).getResponse().getStatus()).as("password not changed").isEqualTo(200);

        // Liên kết phát hành sau lần xác minh lại gắn với lần xác minh mới và dùng được.
        support.ageTokens(account.id(), 120);
        passwordResetService.issue(account.email(), IP);
        String newToken = support.tokenFromLatestMail(account.email());
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(reset(newToken, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void writeAppliedThenExceptionLeavesTokenUnusableRevokesSessionsAndAudits() throws Exception {
        Account account = support.eligible("reset.lost");
        MvcResult before = login(account, account.password());
        Cookie oldAccess = before.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        String token = freshToken(account);
        // Keycloak ĐÃ đổi mật khẩu rồi mới mất kết quả (hết thời gian đọc).
        org.mockito.Mockito.doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("response lost"));
                })
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(502);

        assertThat(support.unusedTokenRows(account.id())).as("token stays consumed").isZero();
        assertThat(writeMarkRow(account).get("password_write_started_at")).as("write-ahead mark committed").isNotNull();
        assertThat(writeMarkRow(account).get("finalized_at")).as("no success audit for an unconfirmed outcome").isNull();
        mockMvc.perform(get("/api/v1/me").cookie(oldAccess)).andExpect(status().isUnauthorized());
        assertThat(keycloakSessions(account)).as("inline fail-closed revocation").isEmpty();
        assertThat(reset(token, "Another#Pass2026").getResponse().getStatus()).as("no replay").isEqualTo(422);
        assertThat(passwordResetAudits(auditRecorder, account)).isZero();

        // Phiên mở bằng mật khẩu mới trước khi job đối soát chạy cũng bị thu hồi; audit được ghi, kết quả chưa xác định.
        MvcResult afterWrite = login(account, NEW_PASSWORD);
        assertThat(afterWrite.getResponse().getStatus()).as("the write was applied").isEqualTo(200);
        Cookie lateAccess = afterWrite.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        support.backdateWriteMark(account.id());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/me").cookie(lateAccess)).andExpect(status().isUnauthorized());
        assertThat(keycloakSessions(account)).isEmpty();
        assertThat(writeMarkRow(account).get("finalized_at")).isNotNull();
        verify(auditRecorder).record(argThat(event -> "PASSWORD_RESET".equals(event.action())
                && account.id().toString().equals(event.targetId())
                && event.outcome() == vn.unicom.thi360.audit.spi.AuditOutcome.FAILURE
                && "PASSWORD_WRITE_INDETERMINATE".equals(event.errorCode())
                && !String.valueOf(event).contains(NEW_PASSWORD)
                && !String.valueOf(event).contains(token)));
        verify(keycloakClient, times(1)).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).as("nothing left").isZero();
    }

    @Test
    void reconciliationRevokesWhenInlineRevocationAlsoFailed() throws Exception {
        Account account = support.eligible("reset.lost.revoke");
        MvcResult before = login(account, account.password());
        Cookie oldAccess = before.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        String token = freshToken(account);
        org.mockito.Mockito.doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("5xx"));
                })
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("logout down")))
                .when(keycloakClient).logoutAllSessions(account.id());

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(502);
        assertThat(keycloakSessions(account)).as("inline revocation failed").isNotEmpty();
        support.backdateWriteMark(account.id());

        org.mockito.Mockito.doCallRealMethod().when(keycloakClient).logoutAllSessions(any());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).isEqualTo(1);

        assertThat(keycloakSessions(account)).isEmpty();
        mockMvc.perform(get("/api/v1/me").cookie(oldAccess)).andExpect(status().isUnauthorized());
        verify(keycloakClient, times(1)).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(writeMarkRow(account).get("finalized_at")).isNotNull();
    }

    @Test
    void crashAfterWriteMarkIsReconciledWithoutReplayingThePassword() throws Exception {
        Account account = support.eligible("reset.crash");
        MvcResult first = login(account, account.password());
        MvcResult second = login(account, account.password());
        Cookie access1 = first.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie access2 = second.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        // Tiến trình chết ngay sau khi dấu ghi trước được commit: chỉ có dấu, không có gì khác.
        Instant claimedAt = Instant.now().minusSeconds(30);
        support.insertToken(account.id(), claimedAt.minusSeconds(60), claimedAt.plus(Duration.ofHours(1)), claimedAt);
        jdbc.update("UPDATE identity.password_reset_token SET password_write_started_at = ? WHERE user_id = ?",
                java.sql.Timestamp.from(claimedAt), account.id());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).as("inside the grace period").isZero();
        mockMvc.perform(get("/api/v1/me").cookie(access1)).andExpect(status().isOk());

        support.backdateWriteMark(account.id());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/me").cookie(access1)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").cookie(access2)).andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("user-sess:" + account.id())).isFalse();
        assertThat(keycloakSessions(account)).isEmpty();
        assertThat(writeMarkRow(account).get("finalized_at")).isNotNull();
        verify(keycloakClient, never()).setPassword(any(), anyString(), anyBoolean());
        verify(auditRecorder, times(1)).record(argThat(event -> "PASSWORD_RESET".equals(event.action())
                && account.id().toString().equals(event.targetId())
                && event.outcome() == vn.unicom.thi360.audit.spi.AuditOutcome.FAILURE));
        assertThat(login(account, account.password()).getResponse().getStatus()).as("password untouched").isEqualTo(200);
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).isZero();
    }

    @Test
    void finalizeFailureIsRecoveredByReconciliation() throws Exception {
        Account account = support.eligible("reset.finalize");
        MvcResult session = login(account, account.password());
        Cookie access = session.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        String token = freshToken(account);
        // Audit lỗi một lần: transaction hoàn tất rollback sau khi Keycloak đã đổi mật khẩu.
        doThrow(new IllegalStateException("audit store down"))
                .doCallRealMethod()
                .when(auditRecorder)
                .record(argThat(event -> event != null && "PASSWORD_RESET".equals(event.action())));

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).as("password was changed").isEqualTo(204);

        assertThat(writeMarkRow(account).get("finalized_at")).as("finalize rolled back").isNull();
        assertThat(writeMarkRow(account).get("password_changed_at")).isNull();
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isUnauthorized());
        assertThat(support.unusedTokenRows(account.id())).isZero();
        assertThat(reset(token, "Another#Pass2026").getResponse().getStatus()).as("no replay").isEqualTo(422);

        support.backdateWriteMark(account.id());
        assertThat(passwordResetService.reconcileUnfinalizedWrites()).isEqualTo(1);

        assertThat(writeMarkRow(account).get("finalized_at")).isNotNull();
        verify(auditRecorder, times(2)).record(argThat(event -> event != null
                && "PASSWORD_RESET".equals(event.action()) && account.id().toString().equals(event.targetId())));
        verify(auditRecorder).record(argThat(event -> event != null
                && "PASSWORD_RESET".equals(event.action())
                && account.id().toString().equals(event.targetId())
                && event.outcome() == vn.unicom.thi360.audit.spi.AuditOutcome.FAILURE));
        verify(keycloakClient, times(1)).setPassword(eq(account.id()), anyString(), anyBoolean());
        assertThat(login(account, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    private java.util.Map<String, Object> writeMarkRow(Account account) {
        return jdbc.queryForMap(
                "SELECT password_write_started_at, password_changed_at, finalized_at, sessions_revoked_at"
                        + " FROM identity.password_reset_token WHERE user_id = ?",
                account.id());
    }

    private List<org.keycloak.representations.idm.UserSessionRepresentation> keycloakSessions(Account account) {
        if (adminClient == null) {
            adminClient = org.keycloak.admin.client.KeycloakBuilder.builder()
                    .serverUrl(keycloakProperties.url())
                    .realm(keycloakProperties.realm())
                    .grantType(org.keycloak.OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(keycloakProperties.clientId())
                    .clientSecret(keycloakProperties.clientSecret())
                    .build();
        }
        return adminClient.realm(keycloakProperties.realm()).users().get(account.id().toString()).getUserSessions();
    }
}
