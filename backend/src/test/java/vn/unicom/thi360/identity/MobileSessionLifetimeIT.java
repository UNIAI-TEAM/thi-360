package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionStore;

/**
 * Verifies AC-12, AC-15, and AC-17 on a dedicated short-timeout Keycloak realm ({@code thi-360-lifetime}) without
 * mutating the shared {@code thi-360} realm. Keycloak adds 120s leeway to idle checks (Q-4 spike), while max
 * lifespan has no leeway; Redis session TTL is shortened to 145s via test properties so Keycloak idle tests
 * (122-125s) prove Keycloak enforcement while AC-17 waits past 145s to prove Redis enforcement.
 */
@TestPropertySource(properties = {
        "thi360.security.session.ttl=PT145S"
})
class MobileSessionLifetimeIT extends AbstractKeycloakIntegrationTest {

    private static final String LIFETIME_REALM = "thi-360-lifetime";
    private static final String MOBILE_CLIENT_ID = "thi360-mobile";
    private static final String MOBILE_CLIENT_SECRET = "thi360-mobile-dev-only";
    private static final String PASSWORD = "Valid@1234";
    private static final String IP = "203.0.113.196";

    static {
        ensureLifetimeRealmExists();
    }

    private static void ensureLifetimeRealmExists() {
        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        boolean exists = admin.realms().findAll().stream()
                .anyMatch(r -> LIFETIME_REALM.equals(r.getRealm()));
        if (exists) {
            return;
        }
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(LIFETIME_REALM);
        realm.setEnabled(true);
        realm.setLoginWithEmailAllowed(true);
        realm.setDuplicateEmailsAllowed(false);
        realm.setSsoSessionIdleTimeout(28800);
        realm.setSsoSessionMaxLifespan(2592000);
        realm.setOfflineSessionIdleTimeout(604800);
        realm.setOfflineSessionMaxLifespanEnabled(true);
        realm.setOfflineSessionMaxLifespan(2592000);
        realm.setPasswordPolicy(
                "length(8) and maxLength(64) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername");
        admin.realms().create(realm);

        var userProfile = admin.realm(LIFETIME_REALM).users().userProfile();
        UPConfig profile = userProfile.getConfiguration();
        for (UPAttribute attr : profile.getAttributes()) {
            if (!"username".equals(attr.getName())) {
                attr.setRequired(null);
            }
        }
        userProfile.update(profile);

        ClientRepresentation apiClient = new ClientRepresentation();
        apiClient.setClientId("thi360-api");
        apiClient.setEnabled(true);
        apiClient.setProtocol("openid-connect");
        apiClient.setPublicClient(false);
        apiClient.setSecret(TEST_APP_CLIENT_SECRET);
        apiClient.setStandardFlowEnabled(false);
        apiClient.setDirectAccessGrantsEnabled(true);
        apiClient.setServiceAccountsEnabled(true);
        apiClient.setProtocolMappers(List.of(apiAudienceMapper()));
        admin.realm(LIFETIME_REALM).clients().create(apiClient).close();

        String serviceAccountId = admin.realm(LIFETIME_REALM).clients()
                .findByClientId("thi360-api").getFirst().getId();
        var saUser = admin.realm(LIFETIME_REALM).clients().get(serviceAccountId).getServiceAccountUser();
        ClientRepresentation realmMgmt = admin.realm(LIFETIME_REALM).clients()
                .findByClientId("realm-management").getFirst();
        List<RoleRepresentation> roles = List.of(
                admin.realm(LIFETIME_REALM).clients().get(realmMgmt.getId()).roles().get("manage-users").toRepresentation(),
                admin.realm(LIFETIME_REALM).clients().get(realmMgmt.getId()).roles().get("view-users").toRepresentation(),
                admin.realm(LIFETIME_REALM).clients().get(realmMgmt.getId()).roles().get("query-users").toRepresentation(),
                admin.realm(LIFETIME_REALM).clients().get(realmMgmt.getId()).roles().get("view-clients").toRepresentation());
        admin.realm(LIFETIME_REALM).users().get(saUser.getId())
                .roles().clientLevel(realmMgmt.getId()).add(roles);

        ClientRepresentation mobileClient = new ClientRepresentation();
        mobileClient.setClientId(MOBILE_CLIENT_ID);
        mobileClient.setEnabled(true);
        mobileClient.setProtocol("openid-connect");
        mobileClient.setPublicClient(false);
        mobileClient.setSecret(MOBILE_CLIENT_SECRET);
        mobileClient.setStandardFlowEnabled(false);
        mobileClient.setDirectAccessGrantsEnabled(true);
        mobileClient.setServiceAccountsEnabled(false);
        mobileClient.setDefaultClientScopes(List.of(
                "web-origins", "acr", "profile", "roles", "basic", "email", "offline_access"));
        mobileClient.setProtocolMappers(List.of(apiAudienceMapper()));
        admin.realm(LIFETIME_REALM).clients().create(mobileClient).close();
    }

    private static ProtocolMapperRepresentation apiAudienceMapper() {
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("thi360-api-audience");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-audience-mapper");
        mapper.setConfig(Map.of(
                "included.client.audience", "thi360-api",
                "id.token.claim", "false",
                "access.token.claim", "true"));
        return mapper;
    }

    @DynamicPropertySource
    static void overrideRealmProperty(DynamicPropertyRegistry registry) {
        registry.add("thi360.keycloak.realm", () -> LIFETIME_REALM);
    }

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

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + IP);
    }

    @Test
    void refreshExpiresInMatchesRealm() throws Exception {
        configureLifetimeRealm(28800, 2592000, 604800, true, 2592000);
        RealmRepresentation prodRealm = KEYCLOAK.getKeycloakAdminClient().realm("thi-360").toRepresentation();
        assertThat(prodRealm.getOfflineSessionIdleTimeout()).isEqualTo(604800);
        assertThat(prodRealm.getOfflineSessionMaxLifespanEnabled()).isTrue();
        assertThat(prodRealm.getOfflineSessionMaxLifespan()).isEqualTo(2592000);
        assertThat(prodRealm.getSsoSessionIdleTimeout()).isEqualTo(28800);

        List<ClientRepresentation> prodMobileClients =
                KEYCLOAK.getKeycloakAdminClient().realm("thi-360").clients().findByClientId(MOBILE_CLIENT_ID);
        assertThat(prodMobileClients).hasSize(1);
        assertThat(prodMobileClients.getFirst().getDefaultClientScopes()).contains("offline_access");

        String username = createUser("life.match");
        JsonNode loginBody = mobileLogin(username);
        assertThat(loginBody.path("refreshExpiresIn").asInt())
                .isEqualTo(prodRealm.getOfflineSessionIdleTimeout());
    }

    @Test
    void mobileEndsAtMaxLifetime() throws Exception {
        configureLifetimeRealm(60, 400, 60, true, 8);
        String username = createUser("life.max");

        JsonNode login = mobileLogin(username);
        String rt = login.path("refreshToken").asText();
        String sid = JWTParser.parse(login.path("accessToken").asText()).getJWTClaimsSet().getStringClaim("sid");

        Thread.sleep(3_000L);
        JsonNode refreshed1 = mobileRefreshOk(rt);
        rt = refreshed1.path("refreshToken").asText();

        Thread.sleep(3_000L);
        JsonNode refreshed2 = mobileRefreshOk(rt);
        rt = refreshed2.path("refreshToken").asText();

        String finalRt = rt;
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(sessionStore.findSession(sid)).isPresent();
                    mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", finalRt))))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
                });
    }

    @Test
    void mobileIdleExpires() throws Exception {
        configureLifetimeRealm(60, 400, 4, false, 2592000);
        String username = createUser("life.mobidle");

        JsonNode login = mobileLogin(username);
        String rt = login.path("refreshToken").asText();
        String sid = JWTParser.parse(login.path("accessToken").asText()).getJWTClaimsSet().getStringClaim("sid");

        Thread.sleep(6_000L);

        assertThat(sessionStore.findSession(sid))
                .as("Redis session must still be live so expiration comes from Keycloak offline idle timeout")
                .isPresent();
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", rt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void mobileOutlivesIdleWeb() throws Exception {
        configureLifetimeRealm(2, 400, 180, false, 2592000);
        String username = createUser("life.outlive");

        MvcResult webLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webRt = webLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie webXsrf = webLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(webRt).isNotNull();
        assertThat(webXsrf).isNotNull();

        JsonNode mobileLogin = mobileLogin(username);
        String mobileRt = mobileLogin.path("refreshToken").asText();

        // Web idle (2 + 120s Keycloak leeway = 122s) expires before mobile offline idle (180s) and Redis TTL (145s).
        Thread.sleep(124_000L);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(webRt, webXsrf)
                        .header(AuthCookieService.XSRF_HEADER, webXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        JsonNode refreshedMobile = mobileRefreshOk(mobileRt);
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedMobile.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void webIdleStillExpires() throws Exception {
        configureLifetimeRealm(2, 400, 300, false, 2592000);
        String username = createUser("life.webidle");

        MvcResult webLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webAt = webLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie webRt = webLogin.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie webXsrf = webLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(webAt).isNotNull();
        assertThat(webRt).isNotNull();
        assertThat(webXsrf).isNotNull();
        String sid = JWTParser.parse(webAt.getValue()).getJWTClaimsSet().getStringClaim("sid");

        Thread.sleep(124_000L);

        assertThat(sessionStore.findSession(sid)).isPresent();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(webRt, webXsrf)
                        .header(AuthCookieService.XSRF_HEADER, webXsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void redisRecordExpiryEndsSession() throws Exception {
        configureLifetimeRealm(60, 400, 300, false, 2592000);
        String username = createUser("life.redis");

        JsonNode login = mobileLogin(username);
        String rt = login.path("refreshToken").asText();
        String sid = JWTParser.parse(login.path("accessToken").asText()).getJWTClaimsSet().getStringClaim("sid");
        assertThat(sessionStore.findSession(sid)).isPresent();

        // Refresh mid-way to prove refresh does not extend Redis TTL while keeping Keycloak offline idle fresh.
        Thread.sleep(60_000L);
        JsonNode midRefresh = mobileRefreshOk(rt);
        rt = midRefresh.path("refreshToken").asText();

        Awaitility.await()
                .atMost(Duration.ofSeconds(100))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> sessionStore.findSession(sid).isEmpty());

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", rt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        // Prove Keycloak itself still accepted the offline refresh token when backend rejected it via Redis cap.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", MOBILE_CLIENT_ID);
        form.add("client_secret", MOBILE_CLIENT_SECRET);
        form.add("refresh_token", rt);
        String kcResponse = RestClient.create()
                .post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/realms/" + LIFETIME_REALM + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        assertThat(objectMapper.readTree(kcResponse).path("access_token").asText()).isNotBlank();
    }

    private void configureLifetimeRealm(
            int ssoIdleSeconds,
            int ssoMaxSeconds,
            int offlineIdleSeconds,
            boolean offlineMaxEnabled,
            int offlineMaxSeconds) {
        var realmResource = KEYCLOAK.getKeycloakAdminClient().realm(LIFETIME_REALM);
        RealmRepresentation rep = realmResource.toRepresentation();
        rep.setSsoSessionIdleTimeout(ssoIdleSeconds);
        rep.setSsoSessionMaxLifespan(ssoMaxSeconds);
        rep.setOfflineSessionIdleTimeout(offlineIdleSeconds);
        rep.setOfflineSessionMaxLifespanEnabled(offlineMaxEnabled);
        rep.setOfflineSessionMaxLifespan(offlineMaxSeconds);
        realmResource.update(rep);
    }

    private JsonNode mobileLogin(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode mobileRefreshOk(String refreshToken) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String createUser(String prefix) {
        String username = prefix + "." + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Lifetime", "Student", PASSWORD, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Lifetime Student",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
        return username;
    }
}
