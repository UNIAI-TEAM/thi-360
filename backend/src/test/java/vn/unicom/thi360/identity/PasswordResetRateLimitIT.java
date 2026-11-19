package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * Giới hạn theo IP của {@code forgot} (E16) và {@code reset} (TM 13): AC-10, AC-11 của plan ID-2 với Redis thật.
 * Ngưỡng mặc định 60 lần mỗi phút; biên kiểm ở lần gọi thứ 60 (qua) và 61 (429).
 */
class PasswordResetRateLimitIT extends AbstractIntegrationTest {

    private static final String IP_A = "203.0.113.180";
    private static final String IP_B = "203.0.113.181";
    private static final String FORGOT = "/api/v1/auth/password/forgot";
    private static final String RESET = "/api/v1/auth/password/reset";

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void clearBuckets() {
        for (String ip : List.of(IP_A, IP_B)) {
            redis.delete(List.of(RateLimitFilter.FORGOT_KEY_PREFIX + ip, RateLimitFilter.RESET_KEY_PREFIX + ip));
        }
    }

    private MockHttpServletResponse call(String path, String ip, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse forgot(String ip) throws Exception {
        return call(FORGOT, ip, "{\"email\":\"nobody@thi360-test.example\"}");
    }

    private MockHttpServletResponse reset(String ip) throws Exception {
        return call(RESET, ip, "{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}");
    }

    @Test
    void forgotPerIp() throws Exception {
        for (int i = 1; i <= 60; i++) {
            assertThat(forgot(IP_A).getStatus()).as("call %d", i).isEqualTo(202);
        }

        MockHttpServletResponse sixtyFirst = forgot(IP_A);

        assertThat(sixtyFirst.getStatus()).isEqualTo(429);
        assertThat(sixtyFirst.getContentAsString()).contains("RATE_LIMITED");
        assertThat(sixtyFirst.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(sixtyFirst.getHeader("Retry-After"))).isBetween(1L, 61L);
        // IP khác không bị ảnh hưởng; reset của chính IP bị chặn dùng bộ đếm riêng.
        assertThat(forgot(IP_B).getStatus()).isEqualTo(202);
        assertThat(reset(IP_A).getStatus()).isEqualTo(422);
    }

    @Test
    void resetPerIp() throws Exception {
        for (int i = 1; i <= 60; i++) {
            assertThat(reset(IP_A).getStatus()).as("call %d", i).isEqualTo(422);
        }

        MockHttpServletResponse sixtyFirst = reset(IP_A);

        assertThat(sixtyFirst.getStatus()).isEqualTo(429);
        assertThat(sixtyFirst.getContentAsString()).contains("RATE_LIMITED");
        assertThat(sixtyFirst.getHeader("Retry-After")).isNotNull();
        assertThat(reset(IP_B).getStatus()).isEqualTo(422);
        assertThat(forgot(IP_A).getStatus()).isEqualTo(202);
    }
}
