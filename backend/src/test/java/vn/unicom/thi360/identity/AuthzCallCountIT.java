package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
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
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.CtxCookieSigner;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.TestSecurity;
@Import(TestAuthzEndpoints.class)
class AuthzCallCountIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.91";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TestSecurity.LettuceCommandCounter lettuceCommandCounter;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CtxCookieSigner ctxCookieSigner;
    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void adminRequestWithCachedSnapshot_usesOneRedisCommandAndZeroSqlQueries() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ops.count." + suffix;
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Ops", "Count", "Valid@1234", true, false, true));

        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Ops Count",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));

        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(userId, opsRole.getId(), userId, Instant.now()));

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie ctxCookie = loginRes.getResponse().getCookie(AuthCookieService.CTX_COOKIE);
        assertThat(atCookie).isNotNull();
        assertThat(ctxCookie).isNotNull();

        CtxCookieSigner.CtxClaims ctxClaims = ctxCookieSigner.verify(ctxCookie.getValue()).orElseThrow();
        assertThat(ctxClaims.access()).isEqualTo(Access.MANAGE);
        assertThat(ctxClaims.version()).isGreaterThanOrEqualTo(1L);

        // Warmup request ensures Lua script SHA is loaded in Redis and Caffeine cache is populated
        mockMvc.perform(get("/api/v1/admin/__test/user-view").cookie(atCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("user-view"));

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean prevStats = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        lettuceCommandCounter.resetAndStart();
        try {
            mockMvc.perform(get("/api/v1/admin/__test/user-view").cookie(atCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value("user-view"));

            assertThat(lettuceCommandCounter.commands()).hasSize(1);
            assertThat(stats.getPrepareStatementCount()).isZero();
        } finally {
            lettuceCommandCounter.stop();
            stats.setStatisticsEnabled(prevStats);
        }
    }

    @Test
    void platformAdminRequest_usesOneRedisSessionCommandAndZeroSqlQueries() throws Exception {
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        mockMvc.perform(get("/api/v1/admin/__test/user-view").cookie(atCookie))
                .andExpect(status().isOk());

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean prevStats = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        lettuceCommandCounter.resetAndStart();
        try {
            mockMvc.perform(get("/api/v1/admin/__test/user-view").cookie(atCookie))
                    .andExpect(status().isOk());

            assertThat(lettuceCommandCounter.commands()).hasSize(1);
            assertThat(stats.getPrepareStatementCount()).isZero();
        } finally {
            lettuceCommandCounter.stop();
            stats.setStatisticsEnabled(prevStats);
        }
    }
}
