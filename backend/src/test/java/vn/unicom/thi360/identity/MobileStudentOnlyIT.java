package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class MobileStudentOnlyIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final String IP_A = "203.0.113.190";
    private static final String IP_B = "203.0.113.191";

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(List.of(RateLimitFilter.KEY_PREFIX + IP_A, RateLimitFilter.KEY_PREFIX + IP_B));
    }

    @Test
    void manageAndPlatformAdminRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String opsUsername = "mob.ops." + suffix;
        UserAccount opsAccount = createUser(opsUsername, PASSWORD);
        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(opsAccount.getId(), opsRole.getId(), opsAccount.getId(), Instant.now()));
        authzSnapshotService.evict(opsAccount.getId());

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", opsUsername, "password", "Wrong@9999"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        MvcResult manageRes = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", opsUsername, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APP_STUDENT_ONLY"))
                .andReturn();
        assertThat(manageRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken");
        assertThat(redisTemplate.opsForZSet().zCard("user-sess:" + opsAccount.getId())).isIn(null, 0L);

        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        assertThat(admin.realm("thi-360").users().get(opsAccount.getId().toString()).getUserSessions()).isEmpty();
        assertThat(offlineSessionsFor(admin, "thi-360", opsAccount.getId().toString(), "thi360-mobile")).isEmpty();

        String platformId = admin.realm("master").users().searchByUsername("platform", true).getFirst().getId();
        int platformOnlineBefore = admin.realm("master").users().get(platformId).getUserSessions().size();

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "platform", "password", "Wrong@9999"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_MASTER_NO_ROLE_USER, "password", TEST_MASTER_NO_ROLE_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        MvcResult platformRes = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "platform", "password", TEST_PLATFORM_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APP_STUDENT_ONLY"))
                .andReturn();
        assertThat(platformRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken");
        assertThat(redisTemplate.opsForZSet().zCard("user-sess:" + platformId)).isIn(null, 0L);
        assertThat(admin.realm("master").users().get(platformId).getUserSessions().size())
                .isEqualTo(platformOnlineBefore);
    }

    @Test
    void refreshAfterRoleGrant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "mob.promote." + suffix;
        UserAccount account = createUser(username, PASSWORD);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(loginRes.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(account.getId(), opsRole.getId(), account.getId(), Instant.now()));
        authzSnapshotService.evict(account.getId());

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APP_STUDENT_ONLY"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        assertThat(offlineSessionsFor(admin, "thi-360", account.getId().toString(), "thi360-mobile")).isEmpty();
    }

    private static List<UserSessionRepresentation> offlineSessionsFor(
            Keycloak admin, String realm, String userId, String clientId) {
        List<ClientRepresentation> clients = admin.realm(realm).clients().findByClientId(clientId);
        if (clients.isEmpty()) {
            return List.of();
        }
        List<UserSessionRepresentation> sessions =
                admin.realm(realm).users().get(userId).getOfflineSessions(clients.getFirst().getId());
        return sessions == null ? List.of() : sessions;
    }

    private UserAccount createUser(String username, String password) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Role", "Check", password, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        return userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Role Check",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
    }
}
