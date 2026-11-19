package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.AuthzSnapshotService;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.Realm;

/** TM 4 / plan C-0: a successful login stores the language of the {@code thi360_locale} cookie. */
class PreferredLocaleLoginIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final String CLIENT_IP = "203.0.113.141";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private AuthzSnapshotService authzSnapshotService;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBucket() {
        redisTemplate.delete(List.of(RateLimitFilter.KEY_PREFIX + CLIENT_IP));
    }

    @Test
    void loginStoresLocaleFromCookie() throws Exception {
        UserAccount account = createUser();
        assertThat(storedLocale(account)).isEqualTo("vi");

        login(account.getUsername(), PASSWORD, new Cookie("thi360_locale", "en")).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        login(account.getUsername(), PASSWORD, new Cookie("thi360_locale", "vi")).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("vi");
    }

    @Test
    void missingOrUnknownCookieKeepsStoredLocale() throws Exception {
        UserAccount account = createUser();
        jdbcTemplate.update("UPDATE identity.user_account SET preferred_locale = 'en' WHERE id = ?", account.getId());

        login(account.getUsername(), PASSWORD, (Cookie) null).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        for (String unknown : List.of("fr", "", "EN", "en-US")) {
            login(account.getUsername(), PASSWORD, new Cookie("thi360_locale", unknown))
                    .andExpect(status().isOk());
            assertThat(storedLocale(account)).as("cookie '%s'", unknown).isEqualTo("en");
        }
    }

    @Test
    void failedLoginDoesNotTouchLocale() throws Exception {
        UserAccount account = createUser();

        login(account.getUsername(), "Wrong@1234", new Cookie("thi360_locale", "en"))
                .andExpect(status().isUnauthorized());

        assertThat(storedLocale(account)).isEqualTo("vi");
    }

    /** R1-5: the credential is right but reading the authorization snapshot fails; no success side effect. */
    @Test
    void authzFailureAfterCredentialCheckLeavesLocaleUnchanged() throws Exception {
        UserAccount account = createUser();
        jdbcTemplate.update("UPDATE identity.user_account SET preferred_locale = 'en' WHERE id = ?", account.getId());
        Long loginAuditsBefore = loginAuditCount(account);
        doThrow(new IllegalStateException("authz snapshot query failed"))
                .when(authzSnapshotService).resolve(any(), eq(Realm.APP));

        int outcome;
        try {
            outcome = login(account.getUsername(), PASSWORD, new Cookie("thi360_locale", "vi"))
                    .andReturn().getResponse().getStatus();
        } catch (ServletException ex) {
            outcome = 500;
        }

        assertThat(outcome).isGreaterThanOrEqualTo(500);
        assertThat(storedLocale(account)).isEqualTo("en");
        assertThat(loginAuditCount(account)).isEqualTo(loginAuditsBefore);
    }

    private Long loginAuditCount(UserAccount account) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'identity' AND action = 'LOGIN'"
                        + " AND target_id = ?",
                Long.class, account.getId().toString());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password, Cookie localeCookie) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/login")
                .header(ClientIpResolver.X_FORWARDED_FOR, CLIENT_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (localeCookie != null) {
            request.cookie(localeCookie);
        }
        return mockMvc.perform(request);
    }

    private String storedLocale(UserAccount account) {
        return jdbcTemplate.queryForObject(
                "SELECT preferred_locale FROM identity.user_account WHERE id = ?", String.class, account.getId());
    }

    private UserAccount createUser() {
        String username = "locale." + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Locale User", "User", PASSWORD, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        return userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Locale User",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
    }
}
