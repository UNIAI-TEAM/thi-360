package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.CtxCookieSigner;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class SessionIT extends AbstractKeycloakIntegrationTest {
    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CtxCookieSigner ctxCookieSigner;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + "203.0.113.70",
                RateLimitFilter.KEY_PREFIX + "203.0.113.71"));
    }

    @Test
    void sixthLoginEvictsOldestSession_andKeepsLatestFiveActive() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "sess." + suffix;
        createUser(username, "Valid@1234");

        List<MvcResult> logins = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.70")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            logins.add(res);
        }

        Cookie evictedAt = logins.get(0).getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie evictedRt = logins.get(0).getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie evictedXsrf = logins.get(0).getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(evictedAt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(evictedRt, evictedXsrf)
                        .header(AuthCookieService.XSRF_HEADER, evictedXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        for (int i = 1; i < 6; i++) {
            Cookie activeAt = logins.get(i).getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
            mockMvc.perform(get("/api/v1/me").cookie(activeAt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username));
        }
    }

    @Test
    void writeRequestWithAuthCookie_requiresMatchingXsrfToken_andAdminOriginCheck() throws Exception {
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.71")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie at = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        Cookie ctx = loginRes.getResponse().getCookie(AuthCookieService.CTX_COOKIE);

        assertThat(ctxCookieSigner.verify(ctx.getValue())).isPresent();
        String tamperedCtx = tamperOneChar(ctx.getValue());
        assertThat(ctxCookieSigner.verify(tamperedCtx)).isEmpty();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(at, xsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_INVALID"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_INVALID"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private static String tamperOneChar(String value) {
        char last = value.charAt(value.length() - 1);
        char replacement = last == 'a' ? 'b' : 'a';
        return value.substring(0, value.length() - 1) + replacement;
    }

    private void createUser(String username, String password) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Session Test", "User", password, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount account = new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Session Test",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId);
        userAccountRepository.save(account);
    }
}
