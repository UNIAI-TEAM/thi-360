package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionStore;

class AuthFlowIT extends AbstractKeycloakIntegrationTest {
    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;


    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + "203.0.113.60",
                RateLimitFilter.KEY_PREFIX + "203.0.113.61",
                RateLimitFilter.KEY_PREFIX + "203.0.113.62",
                RateLimitFilter.KEY_PREFIX + "203.0.113.63",
                RateLimitFilter.KEY_PREFIX + "203.0.113.64",
                RateLimitFilter.KEY_PREFIX + "203.0.113.65",
                RateLimitFilter.KEY_PREFIX + "203.0.113.66",
                RateLimitFilter.KEY_PREFIX + "203.0.113.67",
                RateLimitFilter.KEY_PREFIX + "203.0.113.68"));
    }
    @Test
    void loginWithUsernameEmailAndMixedCase_setsCookies_andMeReturnsAppExamUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "Student.Flow." + suffix;
        String email = "Student.Flow." + suffix + "@thi360.vn";
        UserAccount account = createProvisionedUser(username, email, "Nguyễn Văn A", "Valid@1234", UserStatus.ACTIVE, false);

        for (String identifier : List.of(username, username.toLowerCase(), username.toUpperCase(), email, email.toUpperCase())) {
            MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.60")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + identifier + "\",\"password\":\"Valid@1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.redirectTo").value("/"))
                    .andExpect(jsonPath("$.mustChangePassword").value(false))
                    .andReturn();

            Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
            Cookie rtCookie = loginRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
            Cookie ctxCookie = loginRes.getResponse().getCookie(AuthCookieService.CTX_COOKIE);
            Cookie xsrfCookie = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

            assertThat(atCookie).isNotNull();
            assertThat(rtCookie).isNotNull();
            assertThat(ctxCookie).isNotNull();
            assertThat(xsrfCookie).isNotNull();
            assertThat(atCookie.isHttpOnly()).isTrue();
            assertThat(rtCookie.isHttpOnly()).isTrue();
            assertThat(ctxCookie.isHttpOnly()).isTrue();
            assertThat(xsrfCookie.isHttpOnly()).isFalse();

            mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(account.getId().toString()))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                    .andExpect(jsonPath("$.realm").value("APP"))
                    .andExpect(jsonPath("$.access").value("EXAM"))
                    .andExpect(jsonPath("$.permissions").isArray())
                    .andExpect(jsonPath("$.permissions").isEmpty());
        }

        UserAccount reloaded = userAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void wrongPasswordAndUnknownUsername_returnIdenticalInvalidCredentialsBody() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "wrongpw." + suffix;
        createProvisionedUser(username, null, "Wrong PW", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult wrongPwRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"unknown." + suffix + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode wrongPwJson = objectMapper.readTree(wrongPwRes.getResponse().getContentAsString());
        JsonNode unknownJson = objectMapper.readTree(unknownRes.getResponse().getContentAsString());

        assertThat(wrongPwJson.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownJson.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPwJson.get("message").asText()).isEqualTo(unknownJson.get("message").asText());
        assertThat(wrongPwJson.get("fieldErrors")).isEqualTo(unknownJson.get("fieldErrors"));
    }

    @Test
    void inactiveUser_refusedOnlyWhenPasswordCorrect_andRevokedLiveSessionReturns401() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String inactiveUser = "inactive." + suffix;
        createProvisionedUser(inactiveUser, null, "Inactive User", "Valid@1234", UserStatus.INACTIVE, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.65")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + inactiveUser + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.65")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + inactiveUser + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

        String activeUser = "revoked." + suffix;
        UserAccount activeAccount = createProvisionedUser(activeUser, null, "Revoked Live", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.65")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + activeUser + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isOk());

        sessionStore.deleteAllSessions(activeAccount.getId());

        mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void disabledKeycloakUserWithInactiveRow_returnsSameInvalidCredentialsAsUnknown_andThrottlesOnSixthFailure()
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String disabledUser = "disabled.inactive." + suffix;
        createProvisionedUser(disabledUser, null, "Disabled Inactive", "Valid@1234", UserStatus.INACTIVE, false, false);

        MvcResult unknownRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.61")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"unknown." + suffix + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode unknownJson = objectMapper.readTree(unknownRes.getResponse().getContentAsString());

        for (int i = 0; i < 5; i++) {
            MvcResult disabledRes = mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "203.0.113.61")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + disabledUser + "\",\"password\":\"Wrong@9999\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            JsonNode disabledJson = objectMapper.readTree(disabledRes.getResponse().getContentAsString());
            assertThat(disabledJson.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(disabledJson.get("message").asText()).isEqualTo(unknownJson.get("message").asText());
            assertThat(disabledJson.get("fieldErrors")).isEqualTo(unknownJson.get("fieldErrors"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.61")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + disabledUser + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    @Test
    void mustChangePasswordTrue_clearedByChangePassword_afterValidatingPolicyAndOldPassword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "chgpwd." + suffix;
        createProvisionedUser(username, null, "Must Change", "OldPass@123", UserStatus.ACTIVE, true);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.66")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"OldPass@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrfCookie = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(atCookie, xsrfCookie)
                        .header(AuthCookieService.XSRF_HEADER, xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"WrongOld@123\",\"newPassword\":\"NewPass@1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_OLD_PASSWORD"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(atCookie, xsrfCookie)
                        .header(AuthCookieService.XSRF_HEADER, xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"OldPass@123\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(atCookie, xsrfCookie)
                        .header(AuthCookieService.XSRF_HEADER, xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"OldPass@123\",\"newPassword\":\"NewPass@1234\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.66")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"NewPass@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void refreshAndLogout_rotateAndInvalidateSession_andUserDirectoryResolvesUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "refresh." + suffix;
        UserAccount account = createProvisionedUser(username, null, "Refresh User", "Valid@1234", UserStatus.ACTIVE, false);

        assertThat(userDirectory.findById(account.getId()))
                .isPresent()
                .get()
                .satisfies(summary -> {
                    assertThat(summary.username()).isEqualTo(username);
                    assertThat(summary.displayName()).isEqualTo("Refresh User");
                    assertThat(summary.platformAdmin()).isFalse();
                });

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.66")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie at1 = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie rt1 = loginRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie xsrf1 = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(at1, rt1, xsrf1)
                        .header(AuthCookieService.XSRF_HEADER, xsrf1.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("/"))
                .andReturn();

        Cookie at2 = refreshRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie rt2 = refreshRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie xsrf2 = refreshRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(at2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        MvcResult logoutRes = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(at2, rt2, xsrf2)
                        .header(AuthCookieService.XSRF_HEADER, xsrf2.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logoutRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(logoutRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(logoutRes.getResponse().getCookie(AuthCookieService.CTX_COOKIE).getMaxAge()).isZero();
        assertThat(logoutRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE).getMaxAge()).isZero();

        mockMvc.perform(get("/api/v1/me").cookie(at2))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rt2, xsrf2)
                        .header(AuthCookieService.XSRF_HEADER, xsrf2.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void forgedOrUnsignedLogoutCookie_doesNotRevokeVictimSessionOrRecordLogoutAudit() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "victim.logout." + suffix;
        UserAccount account = createProvisionedUser(
                username, null, "Victim Logout", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.62")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie victimAt = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie victimRt = loginRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie victimXsrf = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        JWTClaimsSet victimClaims = JWTParser.parse(victimAt.getValue()).getJWTClaimsSet();
        String unsignedAt = new PlainJWT(victimClaims).serialize();
        String tamperedSigAt = tamperSignature(victimAt.getValue());
        RSAKey rogueKey = new RSAKeyGenerator(2048).keyID("rogue").generate();
        SignedJWT rogueSigned = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rogue").build(),
                victimClaims);
        rogueSigned.sign(new RSASSASigner(rogueKey));
        String rogueAt = rogueSigned.serialize();

        String callerXsrf = "attacker-xsrf-" + suffix;
        for (String forgedAt : List.of(unsignedAt, tamperedSigAt, rogueAt)) {
            MvcResult logoutRes = mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(
                                    new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, forgedAt),
                                    new Cookie(AuthCookieService.XSRF_COOKIE, callerXsrf))
                            .header(AuthCookieService.XSRF_HEADER, callerXsrf))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertThat(logoutRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
        }

        mockMvc.perform(get("/api/v1/me").cookie(victimAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        verify(auditRecorder, never()).record(argThat(event ->
                event != null
                        && "LOGOUT".equals(event.action())
                        && account.getId().toString().equals(event.targetId())));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(victimAt, victimRt, victimXsrf)
                        .header(AuthCookieService.XSRF_HEADER, victimXsrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(victimAt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        verify(auditRecorder).record(argThat(event ->
                event != null
                        && "LOGOUT".equals(event.action())
                        && account.getId().toString().equals(event.targetId())));
    }

    @Test
    void forgedOrUnsignedRefreshCookie_doesNotRevokeVictimSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "victim.refresh." + suffix;
        UserAccount account = createProvisionedUser(
                username, null, "Victim Refresh", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.63")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie victimAt = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie victimRt = loginRes.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie victimXsrf = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        JWTClaimsSet victimAtClaims = JWTParser.parse(victimAt.getValue()).getJWTClaimsSet();
        String unsignedRt = new PlainJWT(new JWTClaimsSet.Builder()
                .issuer(victimAtClaims.getIssuer())
                .subject(victimAtClaims.getSubject())
                .claim("sid", victimAtClaims.getStringClaim("sid"))
                .build()).serialize();
        String tamperedRt = tamperSignature(victimRt.getValue());

        String callerXsrf = "attacker-rt-xsrf-" + suffix;
        for (String forgedRt : List.of(unsignedRt, tamperedRt)) {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(
                                    new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, forgedRt),
                                    new Cookie(AuthCookieService.XSRF_COOKIE, callerXsrf))
                            .header(AuthCookieService.XSRF_HEADER, callerXsrf))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        }

        mockMvc.perform(get("/api/v1/me").cookie(victimAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        MvcResult genuineRefresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(victimRt, victimXsrf)
                        .header(AuthCookieService.XSRF_HEADER, victimXsrf.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRt = genuineRefresh.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie rotatedXsrf = genuineRefresh.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        sessionStore.deleteAllSessions(account.getId());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedRt, rotatedXsrf)
                        .header(AuthCookieService.XSRF_HEADER, rotatedXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void refreshOnlyLogout_revokesOwnedSessionAndRejectsRefresh_whileForgedRefreshLeavesVictimIntactAndUnlogged()
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownedUsername = "owned.rtlogout." + suffix;
        UserAccount ownedAccount = createProvisionedUser(
                ownedUsername, null, "Owned RT Logout", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult ownedLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.67")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + ownedUsername + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie ownedAt = ownedLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie ownedRt = ownedLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie ownedXsrf = ownedLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(ownedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ownedUsername));

        MvcResult ownedLogout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(ownedRt, ownedXsrf)
                        .header(AuthCookieService.XSRF_HEADER, ownedXsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(ownedLogout.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(ownedLogout.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(ownedLogout.getResponse().getCookie(AuthCookieService.CTX_COOKIE).getMaxAge()).isZero();
        assertThat(ownedLogout.getResponse().getCookie(AuthCookieService.XSRF_COOKIE).getMaxAge()).isZero();

        mockMvc.perform(get("/api/v1/me").cookie(ownedAt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(ownedRt, ownedXsrf)
                        .header(AuthCookieService.XSRF_HEADER, ownedXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        verify(auditRecorder).record(argThat(event ->
                event != null
                        && "LOGOUT".equals(event.action())
                        && ownedAccount.getId().toString().equals(event.targetId())));

        String victimUsername = "victim.rtlogout." + suffix;
        UserAccount victimAccount = createProvisionedUser(
                victimUsername, null, "Victim RT Logout", "Valid@1234", UserStatus.ACTIVE, false);

        MvcResult victimLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.68")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + victimUsername + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie victimAt = victimLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie victimRt = victimLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie victimXsrf = victimLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        JWTClaimsSet victimAtClaims = JWTParser.parse(victimAt.getValue()).getJWTClaimsSet();
        JWTClaimsSet forgedClaims = new JWTClaimsSet.Builder()
                .issuer(victimAtClaims.getIssuer())
                .subject(victimAtClaims.getSubject())
                .claim("sid", victimAtClaims.getStringClaim("sid"))
                .build();
        String unsignedRt = new PlainJWT(forgedClaims).serialize();
        String tamperedRt = tamperSignature(victimRt.getValue());
        RSAKey rogueKey = new RSAKeyGenerator(2048).keyID("rogue-rt-" + suffix).generate();
        SignedJWT rogueSigned = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rogue-rt-" + suffix).build(),
                forgedClaims);
        rogueSigned.sign(new RSASSASigner(rogueKey));
        String rogueRt = rogueSigned.serialize();

        String callerXsrf = "attacker-rt-logout-xsrf-" + suffix;
        for (String forgedRt : List.of(unsignedRt, tamperedRt, rogueRt)) {
            MvcResult forgedLogout = mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(
                                    new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, forgedRt),
                                    new Cookie(AuthCookieService.XSRF_COOKIE, callerXsrf))
                            .header(AuthCookieService.XSRF_HEADER, callerXsrf))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertThat(forgedLogout.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
        }

        mockMvc.perform(get("/api/v1/me").cookie(victimAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(victimUsername));

        verify(auditRecorder, never()).record(argThat(event ->
                event != null
                        && "LOGOUT".equals(event.action())
                        && victimAccount.getId().toString().equals(event.targetId())));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(victimRt, victimXsrf)
                        .header(AuthCookieService.XSRF_HEADER, victimXsrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("/"));
    }

    private static String tamperSignature(String token) {
        int lastDot = token.lastIndexOf('.');
        int mid = lastDot + 1 + (token.length() - lastDot - 1) / 2;
        char ch = token.charAt(mid);
        char replacement = ch == 'A' ? 'B' : 'A';
        return token.substring(0, mid) + replacement + token.substring(mid + 1);
    }

    private UserAccount createProvisionedUser(
            String username,
            String email,
            String fullName,
            String password,
            UserStatus status,
            boolean mustChangePassword) {
        return createProvisionedUser(username, email, fullName, password, status, mustChangePassword, true);
    }

    private UserAccount createProvisionedUser(
            String username,
            String email,
            String fullName,
            String password,
            UserStatus status,
            boolean mustChangePassword,
            boolean enabledInKeycloak) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, fullName, "User", password, enabledInKeycloak, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String code = String.format("U%06d", seq);
        UserAccount account = new UserAccount(
                userId,
                code,
                username,
                fullName,
                email,
                null,
                UserSource.ADMIN_CREATED,
                status,
                mustChangePassword,
                Instant.now(),
                userId);
        return userAccountRepository.save(account);
    }
}
