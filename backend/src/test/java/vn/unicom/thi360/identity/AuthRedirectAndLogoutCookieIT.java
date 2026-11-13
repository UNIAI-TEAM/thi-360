package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "thi360.cookie.domain=.thi360.vn")
class AuthRedirectAndLogoutCookieIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.140";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void login_returnsRoleBasedAndCallbackRedirects_meReturnsHomeUrl_andLogoutClearsAllCookiesWithDomain()
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String opsUsername = "ops.redirect." + suffix;
        UserAccount opsAccount = createUser(opsUsername, "Ops Redirect", false);
        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(opsAccount.getId(), opsRole.getId(), null, Instant.now()));

        // MANAGE user without callbackUrl -> web admin URL, /me homeUrl -> web admin URL
        MvcResult opsLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + opsUsername + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3001"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        List<String> loginSetCookies = opsLogin.getResponse().getHeaders("Set-Cookie");
        assertCookieAttributes(loginSetCookies, AuthCookieService.ACCESS_TOKEN_COOKIE, false);
        assertCookieAttributes(loginSetCookies, AuthCookieService.REFRESH_TOKEN_COOKIE, false);
        assertCookieAttributes(loginSetCookies, AuthCookieService.CTX_COOKIE, false);
        assertCookieAttributes(loginSetCookies, AuthCookieService.XSRF_COOKIE, false);

        Cookie opsAt = opsLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie opsRt = opsLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie opsXsrf = opsLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(opsAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("MANAGE"))
                .andExpect(jsonPath("$.homeUrl").value("http://localhost:3001"));

        // Logout clears all 4 cookies with identical Domain=.thi360.vn and Path=/
        MvcResult logoutRes = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(opsAt, opsRt, opsXsrf)
                        .header(AuthCookieService.XSRF_HEADER, opsXsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> logoutSetCookies = logoutRes.getResponse().getHeaders("Set-Cookie");
        assertCookieAttributes(logoutSetCookies, AuthCookieService.ACCESS_TOKEN_COOKIE, true);
        assertCookieAttributes(logoutSetCookies, AuthCookieService.REFRESH_TOKEN_COOKIE, true);
        assertCookieAttributes(logoutSetCookies, AuthCookieService.CTX_COOKIE, true);
        assertCookieAttributes(logoutSetCookies, AuthCookieService.XSRF_COOKIE, true);

        // Valid absolute callbackUrl -> honored; invalid callbackUrl -> ignored in favor of role default
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + opsUsername
                                + "\",\"password\":\"Valid@1234\",\"callbackUrl\":\"http://localhost:3001/users\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3001/users"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + opsUsername
                                + "\",\"password\":\"Valid@1234\",\"callbackUrl\":\"https://evil.example/phish\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3001"));

        // mustChangePassword=true -> /doi-mat-khau preserving only valid callbackUrl
        String changePwdUser = "chgpwd.redirect." + suffix;
        createUser(changePwdUser, "Change Pwd", true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + changePwdUser
                                + "\",\"password\":\"Valid@1234\",\"callbackUrl\":\"http://localhost:3001/roles\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.redirectTo")
                        .value("http://localhost:3000/doi-mat-khau?callbackUrl=http%3A%2F%2Flocalhost%3A3001%2Froles"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + changePwdUser
                                + "\",\"password\":\"Valid@1234\",\"callbackUrl\":\"//evil.example\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3000/doi-mat-khau"));
    }

    private static void assertCookieAttributes(List<String> setCookieHeaders, String cookieName, boolean cleared) {
        String header = setCookieHeaders.stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Set-Cookie header for " + cookieName));
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Domain=.thi360.vn");
        if (cleared) {
            assertThat(header).contains("Max-Age=0");
            assertThat(header).startsWith(cookieName + "=;");
        }
    }

    private UserAccount createUser(String username, String fullName, boolean mustChangePassword) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, fullName, "Test", "Valid@1234", true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String code = String.format("U%06d", seq);
        return userAccountRepository.save(new UserAccount(
                userId,
                code,
                username,
                fullName,
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                mustChangePassword,
                Instant.now(),
                userId));
    }
}
