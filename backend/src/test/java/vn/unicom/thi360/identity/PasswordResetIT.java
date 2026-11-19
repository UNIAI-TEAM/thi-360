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
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
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
    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;
    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private PasswordResetTestSupport support;

    @BeforeEach
    void setUp() {
        support = new PasswordResetTestSupport(keycloakClient, userAccountRepository, jdbc, redis, pepper.pepper());
        clearBuckets();
    }

    @AfterEach
    void cleanup() {
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
        MvcResult session = login(account, account.password());
        assertThat(session.getResponse().getStatus()).isEqualTo(200);
        Cookie access = session.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie refresh = session.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie xsrf = session.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        assertThat(redis.hasKey("user-sess:" + account.id())).isTrue();

        assertThat(reset(freshToken(account), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue()))
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("user-sess:" + account.id())).as("Redis session key deleted").isFalse();
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
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, new RuntimeException("keycloak down")))
                .when(keycloakClient).setPassword(eq(account.id()), anyString(), anyBoolean());

        MvcResult result = reset(token, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(support.unusedTokenRows(account.id())).as("token released").isEqualTo(1);
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        assertThat(passwordResetAudits(auditRecorder, account)).isZero();
        assertThat(login(account, account.password()).getResponse().getStatus()).as("old password still valid").isEqualTo(200);
        verify(keycloakClient, never()).logoutAllSessions(any());
    }
}
