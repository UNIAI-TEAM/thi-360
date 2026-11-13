package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogMdcIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.80";

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
    void cleanupRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void authenticatedAppAndMasterRequests_includeUserIdInAccessCompletionLog_andClearMdc(CapturedOutput output)
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "accesslog." + suffix;
        UUID appUserId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Access", "Log", "Valid@1234", true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                appUserId,
                String.format("U%06d", seq),
                username,
                "Access Log",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                appUserId));

        MvcResult appLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie appAt = appLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);

        String appTraceId = "trace-app-" + suffix;
        mockMvc.perform(get("/api/v1/me")
                        .cookie(appAt)
                        .header(TraceIdFilter.REQUEST_ID_HEADER, appTraceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appUserId.toString()));

        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TraceIdFilter.MDC_USER_ID)).isNull();

        MvcResult masterLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie masterAt = masterLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);

        String masterTraceId = "trace-master-" + suffix;
        MvcResult masterMe = mockMvc.perform(get("/api/v1/me")
                        .cookie(masterAt)
                        .header(TraceIdFilter.REQUEST_ID_HEADER, masterTraceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realm").value("MASTER"))
                .andReturn();
        String platformUserId = com.jayway.jsonpath.JsonPath.read(masterMe.getResponse().getContentAsString(), "$.id");

        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TraceIdFilter.MDC_USER_ID)).isNull();

        String appAccessLine = output.getOut().lines()
                .filter(l -> l.contains(appTraceId) && l.contains("thi360.access"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing APP thi360.access line in:\n" + output.getOut()));
        assertThat(appAccessLine)
                .contains("[trace=" + appTraceId + " user=" + appUserId + "]")
                .contains("GET /api/v1/me")
                .contains("status=\"200\"");

        String masterAccessLine = output.getOut().lines()
                .filter(l -> l.contains(masterTraceId) && l.contains("thi360.access"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing MASTER thi360.access line in:\n" + output.getOut()));
        assertThat(masterAccessLine)
                .contains("[trace=" + masterTraceId + " user=platform:" + platformUserId + "]")
                .contains("GET /api/v1/me")
                .contains("status=\"200\"");
    }
}
