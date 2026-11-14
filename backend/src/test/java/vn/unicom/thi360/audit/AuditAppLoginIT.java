package vn.unicom.thi360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * AC-01 "history is traceable" for the APP realm (UNI-2553, T16): a successful login by a regular
 * account stores an audit row that carries the actor fields and can be found again by actor id.
 * The MASTER (platform) counterpart is {@link AuditPlatformLoginIT}.
 */
class AuditAppLoginIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.72";
    private static final String PASSWORD = "AppLogin@123";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createdUserId;

    @BeforeEach
    void clearRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
        if (createdUserId == null) {
            return;
        }
        try {
            keycloakClient.deleteUser(createdUserId);
        } catch (Exception ignored) {
            // already gone
        }
        jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", createdUserId);
        jdbcTemplate.update("DELETE FROM identity.user_permission WHERE user_id = ?", createdUserId);
        jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", createdUserId);
        jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", createdUserId);
        redisTemplate.delete(java.util.List.of(
                "user-sess:" + createdUserId,
                "user-sess-seq:" + createdUserId,
                "user-rev-gen:" + createdUserId));
        createdUserId = null;
    }

    @Test
    void appRealmLogin_storesAuditRowWithActorFields_findableByActorId() throws Exception {
        String username = "audit.app." + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Audit App", "User", PASSWORD, true, false, true));
        createdUserId = userId;
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Audit App",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));

        Instant beforeLogin = Instant.now().minusSeconds(5);
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        int relayedTotal = 0;
        int batch;
        while ((batch = outboxRelay.relayBatch()) > 0) {
            relayedTotal += batch;
        }
        assertThat(relayedTotal).isGreaterThanOrEqualTo(1);

        MvcResult auditRes = mockMvc.perform(get("/api/v1/admin/audit-events")
                        .with(asPlatformAdmin())
                        .param("actorId", userId.toString())
                        .param("action", "LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andReturn();

        JsonNode event = objectMapper.readTree(auditRes.getResponse().getContentAsString())
                .get("items")
                .get(0);
        assertThat(event.get("id").asText()).isNotBlank();
        assertThat(Instant.parse(event.get("occurredAt").asText())).isAfterOrEqualTo(beforeLogin);
        assertThat(event.get("module").asText()).isEqualTo("identity");
        assertThat(event.get("action").asText()).isEqualTo("LOGIN");
        assertThat(event.get("actorId").asText()).isEqualTo(userId.toString());
        assertThat(event.get("actorUsername").asText()).isEqualTo(username);
        assertThat(event.get("actorType").asText()).isEqualTo("USER");
        assertThat(event.get("targetType").asText()).isEqualTo("USER");
        assertThat(event.get("targetId").asText()).isEqualTo(userId.toString());
        assertThat(event.get("targetLabel").asText()).isEqualTo(username);
        assertThat(event.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(event.path("after").path("realm").asText()).isEqualTo("APP");
    }
}
