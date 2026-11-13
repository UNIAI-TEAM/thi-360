package vn.unicom.thi360.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class AuditCurrentUserActorIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.71";

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

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void passwordChangeWithCurrentUserSession_attributesActorAndIsQueryableByActorId() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "audit.actor." + suffix;
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Audit Actor", "User", "OldPass@123", true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String code = String.format("U%06d", seq);
        userAccountRepository.save(new UserAccount(
                userId,
                code,
                username,
                "Audit Actor",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"OldPass@123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrfCookie = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(atCookie, xsrfCookie)
                        .header(AuthCookieService.XSRF_HEADER, xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"OldPass@123\",\"newPassword\":\"NewPass@1234\"}"))
                .andExpect(status().isNoContent());

        while (outboxRelay.relayBatch() > 0) {
            // drain outbox
        }

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .with(asPlatformAdmin())
                        .param("actorId", userId.toString())
                        .param("action", "PASSWORD_CHANGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].module").value("identity"))
                .andExpect(jsonPath("$.items[0].action").value("PASSWORD_CHANGED"))
                .andExpect(jsonPath("$.items[0].actorId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].actorUsername").value(username))
                .andExpect(jsonPath("$.items[0].actorType").value("USER"))
                .andExpect(jsonPath("$.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.items[0].targetId").value(userId.toString()));
    }
}
