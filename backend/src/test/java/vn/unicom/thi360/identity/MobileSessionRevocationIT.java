package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.support.MailOutbox;

class MobileSessionRevocationIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final String NEW_PASSWORD = "NewValid@5678";
    private static final String MOBILE_CLIENT_ID = "thi360-mobile";
    private static final String MOBILE_CLIENT_SECRET = "thi360-mobile-dev-only";
    private static final String IP = "203.0.113.195";
    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + IP,
                RateLimitFilter.RESET_KEY_PREFIX + IP));
    }

    @Test
    void deactivateRevokesRedisAndKeycloakOfflineSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "mob.deact." + suffix;
        UserAccount account = createVerifiedUser(username, username + "@thi360.vn");

        JsonNode tokens = mobileLogin(username);
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();
        assertThat(offlineSessionsFor(account.getId())).isNotEmpty();

        mockMvc.perform(post("/api/v1/admin/users/" + account.getId() + "/deactivate")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vi phạm quy chế\"}"))
                .andExpect(status().isOk());

        assertSessionCompletelyRevoked(account.getId(), accessToken, refreshToken);
    }

    @Test
    void passwordResetRevokesRedisAndKeycloakOfflineSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "mob.pwreset." + suffix;
        String email = username + "@thi360.vn";
        UserAccount account = createVerifiedUser(username, email);

        JsonNode tokens = mobileLogin(username);
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();
        assertThat(offlineSessionsFor(account.getId())).isNotEmpty();

        passwordResetService.issue(email, IP);
        MailOutbox.Mail resetMail = new MailOutbox(jdbcTemplate).require(email, MailKind.PASSWORD_RESET);
        Matcher matcher = RESET_TOKEN_PATTERN.matcher(resetMail.text());
        assertThat(matcher.find()).as("reset token in email").isTrue();
        String rawResetToken = matcher.group(1);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawResetToken, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        assertSessionCompletelyRevoked(account.getId(), accessToken, refreshToken);
    }

    @Test
    void logoutRevokesRedisAndKeycloakOfflineSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "mob.revlogout." + suffix;
        UserAccount account = createVerifiedUser(username, username + "@thi360.vn");

        JsonNode tokens = mobileLogin(username);
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();
        assertThat(offlineSessionsFor(account.getId())).isNotEmpty();

        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        assertSessionCompletelyRevoked(account.getId(), accessToken, refreshToken);
    }

    private void assertSessionCompletelyRevoked(UUID userId, String accessToken, String refreshToken) throws Exception {
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        assertThat(offlineSessionsFor(userId)).isEmpty();
        assertKeycloakRejectsRefreshToken(refreshToken);
    }

    private void assertKeycloakRejectsRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", MOBILE_CLIENT_ID);
        form.add("client_secret", MOBILE_CLIENT_SECRET);
        form.add("refresh_token", refreshToken);

        assertThatThrownBy(() -> RestClient.create()
                        .post()
                        .uri(KEYCLOAK.getAuthServerUrl() + "/realms/thi-360/protocol/openid-connect/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(ex -> assertThat(((RestClientResponseException) ex).getStatusCode().value())
                        .isBetween(400, 401));
    }

    private List<UserSessionRepresentation> offlineSessionsFor(UUID userId) {
        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        List<ClientRepresentation> clients = admin.realm("thi-360").clients().findByClientId(MOBILE_CLIENT_ID);
        if (clients.isEmpty()) {
            return List.of();
        }
        List<UserSessionRepresentation> sessions =
                admin.realm("thi-360").users().get(userId.toString()).getOfflineSessions(clients.getFirst().getId());
        return sessions == null ? List.of() : sessions;
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

    private UserAccount createVerifiedUser(String username, String email) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Revoke", "Student", PASSWORD, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Instant now = Instant.now();
        UserAccount account = new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Revoke Student",
                email,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                now,
                userId);
        account.markEmailVerified(now, userId);
        return userAccountRepository.save(account);
    }
}
