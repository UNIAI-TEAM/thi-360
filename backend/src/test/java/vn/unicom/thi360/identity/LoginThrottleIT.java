package vn.unicom.thi360.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class LoginThrottleIT extends AbstractKeycloakIntegrationTest {
    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + "203.0.113.101",
                RateLimitFilter.KEY_PREFIX + "203.0.113.102"));
    }
    @Test
    void sixthFailureIn15Minutes_returns429LoginThrottled_caseInsensitive() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "throttle." + suffix;
        createUser(username, "Valid@1234");

        String[] identifiers = {
            username,
            username.toUpperCase(),
            "  " + username + "  ",
            username.toLowerCase(),
            username.toUpperCase()
        };

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.101")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + identifiers[i] + "\",\"password\":\"Wrong@9999\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    @Test
    void successfulLoginBeforeFifthFailure_resetsFailureCounter() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "resetcnt." + suffix;
        createUser(username, "Valid@1234");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.102")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + username + "\",\"password\":\"Wrong@9999\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.102")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"" + username + "\",\"password\":\"Wrong@9999\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Wrong@9999\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    private void createUser(String username, String password) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Throttle", "User", password, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount account = new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Throttle User",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId);
        userAccountRepository.save(account);
    }
}
