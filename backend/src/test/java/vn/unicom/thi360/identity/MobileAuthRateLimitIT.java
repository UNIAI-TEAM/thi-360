package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

@TestPropertySource(properties = {
        "thi360.security.rate-limit.login-per-ip-limit=4",
        "thi360.security.rate-limit.login-per-ip-window=PT1M",
        "thi360.security.rate-limit.register-per-ip-hour-limit=4",
        "thi360.security.rate-limit.register-per-ip-hour-window=PT1H",
        "thi360.security.rate-limit.register-per-ip-day-limit=10",
        "thi360.security.rate-limit.register-per-ip-day-window=PT24H"
})
class MobileAuthRateLimitIT extends AbstractIntegrationTest {

    private static final String WEB_LOGIN = "/api/v1/auth/login";
    private static final String MOBILE_LOGIN = "/api/v1/auth/mobile/login";
    private static final String ENCODED_MOBILE_LOGIN = "/api/v1/auth/mobile/%6cogin";
    private static final String WEB_VERIFY = "/api/v1/auth/register/verify";
    private static final String MOBILE_VERIFY = "/api/v1/auth/mobile/register/verify";
    private static final String ENCODED_MOBILE_VERIFY = "/api/v1/auth/mobile/register/%76erify";

    private static final List<String> TEST_IPS = List.of(
            "203.0.113.191",
            "203.0.113.192",
            "203.0.113.193",
            "203.0.113.194",
            "203.0.113.195",
            "203.0.113.196");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupBuckets() {
        for (String ip : TEST_IPS) {
            redisTemplate.delete(List.of(
                    RateLimitFilter.KEY_PREFIX + ip,
                    RateLimitFilter.REGISTER_KEY_PREFIX + ip));
        }
    }

    @Test
    void loginSharesIpPolicy() throws Exception {
        String ipWebFirst = "203.0.113.191";
        assertThat(postEmpty(WEB_LOGIN, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_LOGIN, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(MOBILE_LOGIN, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(ENCODED_MOBILE_LOGIN, ipWebFirst).getResponse().getStatus()).isEqualTo(400);

        assertRateLimited(postEmpty(MOBILE_LOGIN, ipWebFirst));
        assertRateLimited(postEmpty(WEB_LOGIN, ipWebFirst));

        String ipMobileFirst = "203.0.113.192";
        assertThat(postEmpty(MOBILE_LOGIN, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(MOBILE_LOGIN, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_LOGIN, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_LOGIN, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);

        assertRateLimited(postEmpty(WEB_LOGIN, ipMobileFirst));
        assertRateLimited(postEmpty(MOBILE_LOGIN, ipMobileFirst));

        assertThat(postEmpty(MOBILE_LOGIN, "203.0.113.193").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void registerVerifySharesRegistrationPolicy() throws Exception {
        String ipWebFirst = "203.0.113.194";
        assertThat(postEmpty(WEB_VERIFY, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_VERIFY, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(MOBILE_VERIFY, ipWebFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(ENCODED_MOBILE_VERIFY, ipWebFirst).getResponse().getStatus()).isEqualTo(400);

        assertRateLimited(postEmpty(MOBILE_VERIFY, ipWebFirst));
        assertRateLimited(postEmpty(WEB_VERIFY, ipWebFirst));

        String ipMobileFirst = "203.0.113.195";
        assertThat(postEmpty(MOBILE_VERIFY, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(MOBILE_VERIFY, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_VERIFY, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);
        assertThat(postEmpty(WEB_VERIFY, ipMobileFirst).getResponse().getStatus()).isEqualTo(400);

        assertRateLimited(postEmpty(WEB_VERIFY, ipMobileFirst));

        MvcResult withStaleCookies = mockMvc.perform(post(URI.create(MOBILE_VERIFY))
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.196")
                        .cookie(
                                new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.access.token"),
                                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertThat(withStaleCookies.getResponse().getStatus()).isEqualTo(400);
    }

    private MvcResult postEmpty(String rawPath, String ip) throws Exception {
        return mockMvc.perform(post(URI.create(rawPath))
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
    }

    private static void assertRateLimited(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("RATE_LIMITED");
    }
}
