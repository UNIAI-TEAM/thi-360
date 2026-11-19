package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * AC-8 (E19), qua chuỗi bảo mật thật và Redis thật: hai cửa sổ trong một chính sách, đường dẫn tương đương dùng chung
 * bộ đếm. Cửa sổ giờ là một giờ thật và không có lệnh ngủ: mọi ranh giới đều xa hàng phút so với thời gian chạy test,
 * nên không có ca nào phụ thuộc vào việc cửa sổ trôi qua trong lúc test chạy. Ranh giới ngày (cửa sổ trượt) được kiểm
 * với đồng hồ cố định ở {@code RegistrationRateLimitWindowsIT}.
 */
@TestPropertySource(properties = {
        "thi360.security.rate-limit.register-per-ip-hour-limit=3",
        "thi360.security.rate-limit.register-per-ip-hour-window=PT1H",
        "thi360.security.rate-limit.register-per-ip-day-limit=5",
        "thi360.security.rate-limit.register-per-ip-day-window=PT24H"
})
class SelfRegistrationRateLimitIT extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    /** Dạng tương đương Spring MVC vẫn khớp với {@code /api/v1/auth/register} (giải mã phần trăm). */
    private static final String ENCODED_REGISTER = "/api/v1/auth/%72egister";
    private static final String IP = "203.0.113.171";
    private static final String OTHER_IP = "203.0.113.172";
    private static final String THIRD_IP = "203.0.113.173";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        redisTemplate.delete(List.of(
                RateLimitFilter.REGISTER_KEY_PREFIX + IP,
                RateLimitFilter.REGISTER_KEY_PREFIX + OTHER_IP,
                RateLimitFilter.REGISTER_KEY_PREFIX + THIRD_IP));
    }

    private MockHttpServletRequestBuilder request(String rawPath, String ip) {
        // Thân rỗng: bị từ chối 400 ngay ở máy chủ, nhưng vẫn là một lần gọi tính vào giới hạn.
        return post(URI.create(rawPath))
                .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private MvcResult register(String rawPath, String ip) throws Exception {
        return mockMvc.perform(request(rawPath, ip)).andReturn();
    }

    private static long retryAfter(MvcResult limited) throws Exception {
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getContentAsString()).contains("RATE_LIMITED");
        String header = limited.getResponse().getHeader("Retry-After");
        assertThat(header).as("Retry-After").isNotBlank();
        return Long.parseLong(header);
    }

    @Test
    void hourlyLimitIsPerIpAndKeyOutlivesTheLongestWindow() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertThat(register(REGISTER, IP).getResponse().getStatus()).as("request %d", i).isEqualTo(400);
        }
        long wait = retryAfter(register(REGISTER, IP));
        assertThat(wait).as("hour window").isBetween(3590L, 3600L);
        // Một IP khác không bị ảnh hưởng.
        assertThat(register(REGISTER, OTHER_IP).getResponse().getStatus()).isEqualTo(400);

        // E19: khóa Redis sống theo cửa sổ dài nhất (24 giờ), không hết hạn theo cửa sổ giờ hay sau 2 phút.
        Long ttl = redisTemplate.getExpire(RateLimitFilter.REGISTER_KEY_PREFIX + IP, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(23L * 3600, 24L * 3600);
    }

    /** R1-02: dạng giải mã tương đương và dạng chuẩn chung một bộ đếm. */
    @Test
    void canonicalAndEncodedRoutesShareTheCounter() throws Exception {
        assertThat(register(REGISTER, THIRD_IP).getResponse().getStatus()).isEqualTo(400);
        assertThat(register(ENCODED_REGISTER, THIRD_IP).getResponse().getStatus()).isEqualTo(400);
        assertThat(register(REGISTER, THIRD_IP).getResponse().getStatus()).isEqualTo(400);

        retryAfter(register(ENCODED_REGISTER, THIRD_IP));
        retryAfter(register(REGISTER, THIRD_IP));
    }

    /**
     * R1-02: dạng tương đương cũng công khai như đường chuẩn: cookie cũ không có XSRF không bị 401 hay 403
     * CSRF_INVALID; đường khác dưới {@code /api/v1/auth/} không được miễn.
     */
    @Test
    void encodedRouteIgnoresStaleCookiesLikeTheCanonicalOne() throws Exception {
        for (String path : List.of(REGISTER, ENCODED_REGISTER)) {
            MvcResult result = mockMvc.perform(request(path, OTHER_IP)
                            .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.garbage.token"),
                                    new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale-refresh")))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(400);
        }
        redisTemplate.delete(RateLimitFilter.REGISTER_KEY_PREFIX + OTHER_IP);

        MvcResult sibling = mockMvc.perform(request("/api/v1/auth/register-x", OTHER_IP)
                        .cookie(new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale-refresh")))
                .andReturn();
        assertThat(sibling.getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(redisTemplate.hasKey(RateLimitFilter.REGISTER_KEY_PREFIX + OTHER_IP)).isFalse();
    }
}
