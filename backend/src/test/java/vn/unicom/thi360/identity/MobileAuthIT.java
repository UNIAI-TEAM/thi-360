package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionStore;

@ExtendWith(OutputCaptureExtension.class)
class MobileAuthIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final List<String> TEST_IPS = List.of(
            "203.0.113.180",
            "203.0.113.181",
            "203.0.113.182",
            "203.0.113.183",
            "203.0.113.184",
            "203.0.113.185",
            "203.0.113.186",
            "203.0.113.187");

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(TEST_IPS.stream().map(ip -> RateLimitFilter.KEY_PREFIX + ip).toList());
    }

    @Test
    void loginReturnsTokensInBody() throws Exception {
        String suffix = shortId();
        String username = "mob.student." + suffix;
        String email = username + "@thi360.vn";
        UserAccount account = createProvisionedUser(username, email, "Nguyễn Học Viên", PASSWORD, UserStatus.ACTIVE, false);

        for (String identifier : List.of(username, email.toUpperCase())) {
            MvcResult res = mockMvc.perform(post("/api/v1/auth/mobile/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.180")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", identifier, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(res.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
            JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(body.path("accessToken").asText()).isNotBlank();
            assertThat(body.path("refreshToken").asText()).isNotBlank();
            assertThat(body.path("accessExpiresIn").asInt()).isPositive();
            assertThat(body.path("refreshExpiresIn").asInt()).isEqualTo(604800);
            assertThat(body.path("mustChangePassword").asBoolean()).isFalse();

            mockMvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + body.path("accessToken").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(account.getId().toString()))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.fullName").value("Nguyễn Học Viên"))
                    .andExpect(jsonPath("$.realm").value("APP"))
                    .andExpect(jsonPath("$.access").value("EXAM"));
        }

        String mustChangeUser = "mob.mustchg." + suffix;
        createProvisionedUser(mustChangeUser, null, "Must Change", PASSWORD, UserStatus.ACTIVE, true);
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.180")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", mustChangeUser, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void wrongPasswordSharesThrottleWithWeb() throws Exception {
        String suffix = shortId();
        String userWebToMobile = "mob.thr.w2m." + suffix;
        createProvisionedUser(userWebToMobile, null, "Throttle W2M", PASSWORD, UserStatus.ACTIVE, false);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.181")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", userWebToMobile, "password", "Wrong@9999"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/mobile/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.181")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", userWebToMobile, "password", "Wrong@9999"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.181")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", userWebToMobile, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));

        String userMobileToWeb = "mob.thr.m2w." + suffix;
        createProvisionedUser(userMobileToWeb, null, "Throttle M2W", PASSWORD, UserStatus.ACTIVE, false);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/mobile/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.182")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", userMobileToWeb, "password", "Wrong@9999"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.182")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", userMobileToWeb, "password", "Wrong@9999"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.182")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", userMobileToWeb, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    @Test
    void inactiveAndSoftDeleted() throws Exception {
        String suffix = shortId();
        String inactiveUsername = "mob.inactive." + suffix;
        UserAccount inactive = createProvisionedUser(
                inactiveUsername, null, "Inactive Mobile", PASSWORD, UserStatus.INACTIVE, false);

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.183")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", inactiveUsername, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        assertThat(redisTemplate.opsForZSet().zCard("user-sess:" + inactive.getId())).isIn(null, 0L);

        String deletedUsername = "mob.deleted." + suffix;
        UserAccount deleted = createProvisionedUser(
                deletedUsername, null, "Deleted Mobile", PASSWORD, UserStatus.ACTIVE, false);
        jdbcTemplate.update("UPDATE identity.user_account SET deleted_at = NOW() WHERE id = ?", deleted.getId());

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.183")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", deletedUsername, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(redisTemplate.opsForZSet().zCard("user-sess:" + deleted.getId())).isIn(null, 0L);
    }

    @Test
    void refresh() throws Exception {
        String suffix = shortId();
        String username = "mob.refresh." + suffix;
        UserAccount account = createProvisionedUser(username, null, "Refresh Mobile", PASSWORD, UserStatus.ACTIVE, false);

        JsonNode loginBody = mobileLogin(username, PASSWORD, "203.0.113.184");
        String refreshToken = loginBody.path("refreshToken").asText();

        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(refreshRes.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        JsonNode refreshed = objectMapper.readTree(refreshRes.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(refreshed.path("accessToken").asText()).isNotBlank();
        assertThat(refreshed.path("refreshToken").asText()).isNotBlank();
        assertThat(refreshed.path("refreshExpiresIn").asInt()).isEqualTo(604800);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "not-a-valid-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        MvcResult webLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.184")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webRt = webLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie webXsrf = webLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(webRt).isNotNull();

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", webRt.getValue()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, refreshToken), webXsrf)
                        .header(AuthCookieService.XSRF_HEADER, webXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        sessionStore.deleteAllSessions(account.getId());
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshed.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void concurrentRefreshBothSucceed() throws Exception {
        String suffix = shortId();
        String username = "mob.concur." + suffix;
        createProvisionedUser(username, null, "Concurrent Refresh", PASSWORD, UserStatus.ACTIVE, false);

        JsonNode loginBody = mobileLogin(username, PASSWORD, "203.0.113.185");
        String refreshToken = loginBody.path("refreshToken").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<MvcResult> task = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("refreshToken", refreshToken))))
                        .andReturn();
            };
            Future<MvcResult> f1 = pool.submit(task);
            Future<MvcResult> f2 = pool.submit(task);
            ready.await();
            go.countDown();

            for (MvcResult res : List.of(f1.get(), f2.get())) {
                assertThat(res.getResponse().getStatus()).isEqualTo(200);
                JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
                mockMvc.perform(get("/api/v1/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + body.path("accessToken").asText()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.username").value(username));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        String suffix = shortId();
        String username = "mob.logout." + suffix;
        createProvisionedUser(username, null, "Logout Mobile", PASSWORD, UserStatus.ACTIVE, false);

        JsonNode loginBody = mobileLogin(username, PASSWORD, "203.0.113.186");
        String accessToken = loginBody.path("accessToken").asText();
        String refreshToken = loginBody.path("refreshToken").asText();

        JWTClaimsSet atClaims = JWTParser.parse(accessToken).getJWTClaimsSet();
        JWTClaimsSet forgedClaims = new JWTClaimsSet.Builder()
                .issuer(atClaims.getIssuer())
                .subject(atClaims.getSubject())
                .claim("sid", atClaims.getStringClaim("sid"))
                .build();
        String unsignedRt = new PlainJWT(forgedClaims).serialize();
        RSAKey rogueKey = new RSAKeyGenerator(2048).keyID("rogue-mob-" + suffix).generate();
        SignedJWT rogueSigned = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rogue-mob-" + suffix).build(),
                forgedClaims);
        rogueSigned.sign(new RSASSASigner(rogueKey));

        for (String forged : List.of(unsignedRt, rogueSigned.serialize())) {
            mockMvc.perform(post("/api/v1/auth/mobile/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", forged))))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());
    }

    @Test
    void auditHasChannel(CapturedOutput output) throws Exception {
        String suffix = shortId();
        String username = "mob.audit." + suffix;
        String uniquePassword = "MobAudit!" + suffix;
        UserAccount account = createProvisionedUser(
                username, null, "Audit Mobile", uniquePassword, UserStatus.ACTIVE, false);

        JsonNode loginBody = mobileLogin(username, uniquePassword, "203.0.113.187");
        String accessToken = loginBody.path("accessToken").asText();
        String refreshToken = loginBody.path("refreshToken").asText();

        while (outboxRelay.relayBatch() > 0) {
            // drain outbox to audit_event
        }

        String afterJson = jdbcTemplate.queryForObject(
                "SELECT \"after\"::text FROM audit.audit_event"
                        + " WHERE module = 'identity' AND action = 'LOGIN' AND target_id = ?"
                        + " ORDER BY occurred_at DESC LIMIT 1",
                String.class,
                account.getId().toString());
        assertThat(afterJson).isNotBlank();
        JsonNode after = objectMapper.readTree(afterJson);
        assertThat(after.path("realm").asText()).isEqualTo("APP");
        assertThat(after.path("channel").asText()).isEqualTo("MOBILE");
        assertThat(afterJson)
                .doesNotContain(uniquePassword)
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken);
        assertThat(output.getAll())
                .doesNotContain(uniquePassword)
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken);
    }

    @Test
    void sessionCapCountsMobile() throws Exception {
        String suffix = shortId();
        String username = "mob.cap." + suffix;
        createProvisionedUser(username, null, "Session Cap", PASSWORD, UserStatus.ACTIVE, false);

        List<String> mobileAccessTokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JsonNode body = mobileLogin(username, PASSWORD, "203.0.113.180");
            mobileAccessTokens.add(body.path("accessToken").asText());
        }

        List<Cookie> webAccessCookies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.180")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", username, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andReturn();
            webAccessCookies.add(res.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE));
        }

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mobileAccessTokens.get(0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        for (int i = 1; i < 3; i++) {
            mockMvc.perform(get("/api/v1/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + mobileAccessTokens.get(i)))
                    .andExpect(status().isOk());
        }
        for (Cookie cookie : webAccessCookies) {
            mockMvc.perform(get("/api/v1/me").cookie(cookie))
                    .andExpect(status().isOk());
        }
    }

    private JsonNode mobileLogin(String identifier, String password, String ip) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UserAccount createProvisionedUser(
            String username,
            String email,
            String fullName,
            String password,
            UserStatus status,
            boolean mustChangePassword) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, fullName, "User", password, true, false, true));
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
        return userAccountRepository.save(account);
    }
}
