package vn.unicom.thi360.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import vn.unicom.thi360.identity.application.AuthzSnapshotService;
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
import vn.unicom.thi360.mobilecontract.MobileFixtureCertifier;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionStore;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

public class MobileContractFixturesIT extends AbstractKeycloakIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "auth/login-200.json",
                "auth/login-200-must-change-password.json",
                "auth/login-400-validation-failed.json",
                "auth/login-401-invalid-credentials.json",
                "auth/login-403-account-inactive.json",
                "auth/login-403-app-student-only.json",
                "auth/login-429-login-throttled.json",
                "auth/login-429-rate-limited.json",
                "auth/refresh-200.json",
                "auth/refresh-401-session-revoked.json",
                "auth/refresh-403-app-student-only.json",
                "auth/register-verify-201.json",
                "auth/register-verify-422-email-code-expired.json",
                "auth/register-verify-422-email-code-invalid.json",
                "auth/register-verify-429-rate-limited.json",
                "api/change-password-400-password-weak.json",
                "api/change-password-400-wrong-old-password.json",
                "api/me-200.json",
                "api/me-401-session-revoked.json",
                "api/me-401-unauthenticated.json",
                "api/me-profile-200.json",
                "api/me-profile-200-no-email.json",
                "api/password-forgot-202.json",
                "api/password-forgot-400-validation-failed.json",
                "api/password-reset-400-password-weak.json",
                "api/password-reset-422-reset-token-invalid.json",
                "api/register-200.json",
                "api/register-400-validation-failed.json",
                "api/register-409-email-taken.json",
                "api/register-409-username-taken.json",
                "api/register-429-otp-rate-limited.json",
                "api/register-resend-200.json",
                "api/register-resend-422-email-code-expired.json",
                "api/register-resend-429-rate-limited.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final String PASSWORD = "Valid@1234";
    private static final String IP_MAIN = "203.0.113.205";
    private static final String IP_LOGIN_RL = "203.0.113.206";
    private static final String IP_VERIFY_RL = "203.0.113.207";
    private static final String IP_THROTTLE = "203.0.113.208";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AuthzSnapshotService authzSnapshotService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenPepperProperties tokenPepperProperties;

    private SelfRegistrationSupport support;
    private PasswordResetTestSupport resetSupport;
    private MobileFixtureCertifier certifier;
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        support = new SelfRegistrationSupport(mockMvc, jdbcTemplate, keycloakClient, redisTemplate, objectMapper);
        resetSupport = new PasswordResetTestSupport(
                keycloakClient, userAccountRepository, jdbcTemplate, redisTemplate, tokenPepperProperties.pepper());
        certifier = new MobileFixtureCertifier(objectMapper);
        createdUserIds.clear();
        clearRateLimits();
    }

    @AfterEach
    void tearDown() {
        clearRateLimits();
        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM identity.password_reset_token WHERE user_id = ?", userId);
        }
        resetSupport.cleanup();
        support.cleanup();
        for (UUID userId : createdUserIds) {
            try {
                keycloakClient.deleteUser(userId);
            } catch (RuntimeException ignored) {
                // already deleted
            }
            jdbcTemplate.update("DELETE FROM identity.email_code WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
        }
        createdUserIds.clear();
    }

    private void clearRateLimits() {
        for (String ip : List.of(IP_MAIN, IP_LOGIN_RL, IP_VERIFY_RL, IP_THROTTLE)) {
            redisTemplate.delete(List.of(
                    RateLimitFilter.KEY_PREFIX + ip,
                    RateLimitFilter.REGISTER_KEY_PREFIX + ip,
                    RateLimitFilter.FORGOT_KEY_PREFIX + ip,
                    RateLimitFilter.RESET_KEY_PREFIX + ip));
        }
    }

    @Test
    void allAuthAndApiContractFixturesMatchLiveResponses() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String studentUser = "fix.stu." + suffix;
        String studentEmail = studentUser + "@thi360.vn";
        UserAccount student = createUser(studentUser, studentEmail, "Nguyễn Văn An", UserStatus.ACTIVE, false);

        // --- auth/login-200.json & api/me-200.json & api/me-profile-200.json ---
        MvcResult login200 = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", studentUser, "password", PASSWORD))))
                .andReturn();
        assertFixture("auth/login-200.json", 200, login200, Set.of("accessToken", "refreshToken", "accessExpiresIn"));
        JsonNode loginBody = parseBody(login200);
        String accessToken = loginBody.path("accessToken").asText();
        String refreshToken = loginBody.path("refreshToken").asText();

        MvcResult me200 = mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        assertFixture("api/me-200.json", 200, me200, Set.of("id", "username", "fullName", "homeUrl"));

        MvcResult profile200 = mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        assertFixture("api/me-profile-200.json", 200, profile200, Set.of("username", "fullName", "email"));

        // --- api/me-profile-200-no-email.json ---
        String noEmailUser = "fix.noem." + suffix;
        createUser(noEmailUser, null, "Nguyễn Văn Bình", UserStatus.ACTIVE, false);
        JsonNode noEmailLogin = parseBody(mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", noEmailUser, "password", PASSWORD))))
                .andReturn());
        MvcResult profileNoEmail = mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noEmailLogin.path("accessToken").asText()))
                .andReturn();
        assertFixture("api/me-profile-200-no-email.json", 200, profileNoEmail, Set.of("username", "fullName"));

        // --- auth/login-200-must-change-password.json ---
        String mustChangeUser = "fix.mcp." + suffix;
        createUser(mustChangeUser, null, "Must Change", UserStatus.ACTIVE, true);
        MvcResult loginMustChange = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", mustChangeUser, "password", PASSWORD))))
                .andReturn();
        assertFixture(
                "auth/login-200-must-change-password.json",
                200,
                loginMustChange,
                Set.of("accessToken", "refreshToken", "accessExpiresIn"));

        // --- auth/login-400-validation-failed.json ---
        MvcResult login400 = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertFixture("auth/login-400-validation-failed.json", 400, login400, Set.of("traceId"));

        // --- auth/login-401-invalid-credentials.json & auth/login-429-login-throttled.json ---
        String throttleUser = "fix.thr." + suffix;
        createUser(throttleUser, null, "Throttle Fixture", UserStatus.ACTIVE, false);
        MvcResult login401 = null;
        for (int i = 0; i < 5; i++) {
            login401 = mockMvc.perform(post("/api/v1/auth/mobile/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, IP_THROTTLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", throttleUser, "password", "Wrong@9999"))))
                    .andReturn();
        }
        assertFixture("auth/login-401-invalid-credentials.json", 401, login401, Set.of("traceId"));

        MvcResult login429Throttle = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_THROTTLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", throttleUser, "password", PASSWORD))))
                .andReturn();
        assertFixture("auth/login-429-login-throttled.json", 429, login429Throttle, Set.of("traceId"));

        // --- auth/login-403-account-inactive.json ---
        String inactiveUser = "fix.inact." + suffix;
        createUser(inactiveUser, null, "Inactive Fixture", UserStatus.INACTIVE, false);
        MvcResult login403Inactive = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", inactiveUser, "password", PASSWORD))))
                .andReturn();
        assertFixture("auth/login-403-account-inactive.json", 403, login403Inactive, Set.of("traceId"));

        // --- auth/login-403-app-student-only.json ---
        String managerUser = "fix.mgr." + suffix;
        UserAccount manager = createUser(managerUser, null, "Manager Fixture", UserStatus.ACTIVE, false);
        grantAdminRole(manager.getId());
        MvcResult login403StudentOnly = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", managerUser, "password", PASSWORD))))
                .andReturn();
        assertFixture("auth/login-403-app-student-only.json", 403, login403StudentOnly, Set.of("traceId"));

        // --- auth/login-429-rate-limited.json ---
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/mobile/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, IP_LOGIN_RL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn();
        }
        MvcResult login429RateLimited = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_LOGIN_RL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertFixture("auth/login-429-rate-limited.json", 429, login429RateLimited, Set.of("traceId"));

        // --- auth/refresh-200.json ---
        MvcResult refresh200 = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andReturn();
        assertFixture(
                "auth/refresh-200.json",
                200,
                refresh200,
                Set.of("accessToken", "refreshToken", "accessExpiresIn"));
        String refreshedRt = parseBody(refresh200).path("refreshToken").asText();

        // --- auth/refresh-403-app-student-only.json ---
        String promotedUser = "fix.promo." + suffix;
        UserAccount promoted = createUser(promotedUser, null, "Promoted Fixture", UserStatus.ACTIVE, false);
        JsonNode promotedLogin = parseBody(mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", promotedUser, "password", PASSWORD))))
                .andReturn());
        grantAdminRole(promoted.getId());
        MvcResult refresh403StudentOnly = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", promotedLogin.path("refreshToken").asText()))))
                .andReturn();
        assertFixture("auth/refresh-403-app-student-only.json", 403, refresh403StudentOnly, Set.of("traceId"));

        // --- api/change-password-400-wrong-old-password.json & password-weak ---
        MvcResult chgWrong = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "Wrong@9999", "newPassword", "Brand#New2026"))))
                .andReturn();
        assertFixture("api/change-password-400-wrong-old-password.json", 400, chgWrong, Set.of("traceId"));

        MvcResult chgWeak = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", PASSWORD, "newPassword", "weak"))))
                .andReturn();
        assertFixture("api/change-password-400-password-weak.json", 400, chgWeak, Set.of("traceId"));

        // --- api/me-401-unauthenticated.json & api/me-401-session-revoked.json & auth/refresh-401-session-revoked.json ---
        MvcResult me401Unauth = mockMvc.perform(get("/api/v1/me")).andReturn();
        assertFixture("api/me-401-unauthenticated.json", 401, me401Unauth, Set.of("traceId"));

        sessionStore.deleteAllSessions(student.getId());
        MvcResult me401Revoked = mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        assertFixture("api/me-401-session-revoked.json", 401, me401Revoked, Set.of("traceId"));

        MvcResult refresh401Revoked = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshedRt))))
                .andReturn();
        assertFixture("auth/refresh-401-session-revoked.json", 401, refresh401Revoked, Set.of("traceId"));

        // --- api/register-* & auth/register-verify-* ---
        String regUser = "fix.reg." + suffix;
        String regEmail = regUser + "@thi360.vn";
        String regIp = SelfRegistrationSupport.freshIp();

        MvcResult reg400 = support.startRegistration(
                        Map.of(
                                "username", "ab",
                                "password", "weak",
                                "fullName", "",
                                "email", "not-an-email",
                                "acceptTerms", false,
                                "termsVersion", ""),
                        regIp)
                .andReturn();
        assertFixture("api/register-400-validation-failed.json", 400, reg400, Set.of("traceId"));

        MvcResult reg409User = support.startRegistration(
                        support.body(studentUser, "other." + regEmail), SelfRegistrationSupport.freshIp())
                .andReturn();
        assertFixture("api/register-409-username-taken.json", 409, reg409User, Set.of("traceId"));

        MvcResult reg409Email = support.startRegistration(
                        support.body("other." + regUser, studentEmail), SelfRegistrationSupport.freshIp())
                .andReturn();
        assertFixture("api/register-409-email-taken.json", 409, reg409Email, Set.of("traceId"));

        MvcResult reg200 = support.startRegistration(support.body(regUser, regEmail), regIp).andReturn();
        assertFixture("api/register-200.json", 200, reg200, Set.of("registrationId", "email"));
        UUID regId = UUID.fromString(parseBody(reg200).path("registrationId").asText());

        MvcResult reg429Otp = support.startRegistration(support.body(regUser, regEmail), regIp).andReturn();
        assertFixture("api/register-429-otp-rate-limited.json", 429, reg429Otp, Set.of("traceId", "retryAfter"));

        MvcResult resend429 = support.resendRegistration(regId, regIp).andReturn();
        assertFixture("api/register-resend-429-rate-limited.json", 429, resend429, Set.of("traceId", "retryAfter"));

        jdbcTemplate.update(
                "UPDATE identity.pending_registration_send SET created_at = created_at - interval '61 seconds'"
                        + " WHERE email_normalized = ?",
                regEmail.toLowerCase());
        MvcResult resend200 = support.resendRegistration(regId, regIp).andReturn();
        assertFixture("api/register-resend-200.json", 200, resend200, Set.of("registrationId", "email"));

        MvcResult resend422Expired = support.resendRegistration(UUID.randomUUID(), regIp).andReturn();
        assertFixture("api/register-resend-422-email-code-expired.json", 422, resend422Expired, Set.of("traceId"));

        String otp = support.latestVerifyCode(regEmail);
        String wrongOtp = "000000".equals(otp) ? "111111" : "000000";
        MvcResult verify422Invalid = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, regIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("registrationId", regId, "code", wrongOtp))))
                .andReturn();
        assertFixture("auth/register-verify-422-email-code-invalid.json", 422, verify422Invalid, Set.of("traceId"));

        MvcResult verify422Expired = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, regIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("registrationId", UUID.randomUUID(), "code", "123456"))))
                .andReturn();
        assertFixture("auth/register-verify-422-email-code-expired.json", 422, verify422Expired, Set.of("traceId"));

        MvcResult verify201 = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, regIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("registrationId", regId, "code", otp))))
                .andReturn();
        assertFixture(
                "auth/register-verify-201.json",
                201,
                verify201,
                Set.of("accessToken", "refreshToken", "accessExpiresIn"));

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                            .header(ClientIpResolver.X_FORWARDED_FOR, IP_VERIFY_RL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn();
        }
        MvcResult verify429 = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_VERIFY_RL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertFixture("auth/register-verify-429-rate-limited.json", 429, verify429, Set.of("traceId"));

        // --- api/password-forgot-* & api/password-reset-* ---
        MvcResult forgot202 = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@thi360-test.example"))))
                .andReturn();
        assertFixture("api/password-forgot-202.json", 202, forgot202, Set.of());

        MvcResult forgot400 = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", ""))))
                .andReturn();
        assertFixture("api/password-forgot-400-validation-failed.json", 400, forgot400, Set.of("traceId"));

        Instant now = Instant.now();
        String resetToken = resetSupport.insertToken(
                student.getId(), now.minusSeconds(5), now.plus(java.time.Duration.ofHours(1)), null);
        MvcResult reset400Weak = mockMvc.perform(post("/api/v1/auth/password/reset")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", "weak"))))
                .andReturn();
        assertFixture("api/password-reset-400-password-weak.json", 400, reset400Weak, Set.of("traceId"));

        MvcResult reset422Invalid = mockMvc.perform(post("/api/v1/auth/password/reset")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_MAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "nonexistent-reset-token", "newPassword", "Brand#New2026"))))
                .andReturn();
        assertFixture("api/password-reset-422-reset-token-invalid.json", 422, reset422Invalid, Set.of("traceId"));

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private void assertFixture(
            String relativePath, int expectedStatus, MvcResult actualResult, Set<String> dynamicFields)
            throws Exception {
        certifier.certify(relativePath, expectedStatus, actualResult, dynamicFields);
    }

    private JsonNode parseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private UserAccount createUser(
            String username, String email, String fullName, UserStatus status, boolean mustChangePassword) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, fullName, "User", PASSWORD, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount account = new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                fullName,
                email,
                null,
                UserSource.ADMIN_CREATED,
                status,
                mustChangePassword,
                Instant.now(),
                userId);
        if (email != null) {
            account.markEmailVerified(Instant.now(), userId);
        }
        createdUserIds.add(userId);
        return userAccountRepository.save(account);
    }

    private void grantAdminRole(UUID userId) {
        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(userId, opsRole.getId(), userId, Instant.now()));
        authzSnapshotService.evict(userId);
    }
}
