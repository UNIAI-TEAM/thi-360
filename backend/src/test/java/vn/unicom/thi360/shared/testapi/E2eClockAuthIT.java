package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
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

/**
 * AC-10 và phần JWT của "Ảnh hưởng ngoài task": token do Keycloak cấp theo giờ thật, nên sau khi tua
 * đồng hồ thử 3 giờ, đăng nhập mới vẫn gọi được API cần phiên. Nếu bộ giải mã JWT đọc
 * {@link MutableClock}, token mới (hết hạn sau 5 phút giờ thật) đã quá hạn ngay.
 */
@ActiveProfiles("e2e")
class E2eClockAuthIT extends AbstractKeycloakIntegrationTest {

    private static final String CLOCK = "/api/v1/test/clock";
    private static final String CLIENT_IP = "203.0.113.90";

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
    void resetClockAndRateLimit() throws Exception {
        redisTemplate.delete(List.of(RateLimitFilter.KEY_PREFIX + CLIENT_IP));
        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content("{\"reset\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginAfterThreeHourAdvanceStillCallsSessionApi() throws Exception {
        String username = "e2eclock." + UUID.randomUUID().toString().substring(0, 8);
        createUser(username, "Valid@1234");

        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content("{\"advanceSeconds\":10800}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, CLIENT_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie at = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(at))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void platformLoginAfterAdvanceStillCallsSessionApi() throws Exception {
        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content("{\"advanceSeconds\":10800}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, CLIENT_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie at = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(at)).andExpect(status().isOk());
    }

    private void createUser(String username, String password) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "E2E Clock", "User", password, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "E2E Clock",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
    }
}
