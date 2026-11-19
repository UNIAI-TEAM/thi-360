package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
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
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * AC-8 (E19): giới hạn đăng ký theo IP có hai cửa sổ trong một chính sách. Cửa sổ giờ co lại còn 4 giây để chạm
 * cả hai cửa sổ trong một test mà không phải chờ cả giờ; ngưỡng 30 và 100 là ngưỡng mặc định của E19.
 */
@TestPropertySource(properties = {
        "thi360.security.rate-limit.register-per-ip-hour-limit=30",
        "thi360.security.rate-limit.register-per-ip-hour-window=PT4S",
        "thi360.security.rate-limit.register-per-ip-day-limit=100",
        "thi360.security.rate-limit.register-per-ip-day-window=PT1H"
})
class SelfRegistrationRateLimitIT extends AbstractIntegrationTest {

    private static final String IP = "203.0.113.171";
    private static final String OTHER_IP = "203.0.113.172";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        redisTemplate.delete(List.of(RateLimitFilter.REGISTER_KEY_PREFIX + IP, RateLimitFilter.REGISTER_KEY_PREFIX + OTHER_IP));
    }

    private MvcResult register(String ip) throws Exception {
        // Thân rỗng: bị từ chối 400 ngay ở máy chủ, nhưng vẫn là một lần gọi tính vào giới hạn.
        return mockMvc.perform(post("/api/v1/auth/register")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
    }

    private void expectAllowed(int from, int to) throws Exception {
        for (int i = from; i <= to; i++) {
            assertThat(register(IP).getResponse().getStatus()).as("request %d", i).isEqualTo(400);
        }
    }

    private static long retryAfter(MvcResult limited) throws Exception {
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getContentAsString()).contains("RATE_LIMITED");
        String header = limited.getResponse().getHeader("Retry-After");
        assertThat(header).as("Retry-After").isNotBlank();
        return Long.parseLong(header);
    }

    @Test
    void thirtyFirstAndHundredFirstRegistrationAreLimitedPerIp() throws Exception {
        expectAllowed(1, 30);
        long hourWait = retryAfter(register(IP));
        assertThat(hourWait).isBetween(1L, 5L);
        // Một IP khác không bị ảnh hưởng.
        assertThat(register(OTHER_IP).getResponse().getStatus()).isEqualTo(400);

        for (int round = 2; round <= 3; round++) {
            Thread.sleep(Duration.ofMillis(4200));
            expectAllowed(30 * (round - 1) + 1, 30 * round);
            retryAfter(register(IP));
        }
        Thread.sleep(Duration.ofMillis(4200));
        expectAllowed(91, 100);

        // Lần 101 trong ngày bị chặn dù cửa sổ giờ đã đầy lại: phải chờ theo cửa sổ ngày, không phải cửa sổ giờ.
        long dayWait = retryAfter(register(IP));
        assertThat(dayWait).isGreaterThan(120L);

        // E19: khóa Redis sống theo cửa sổ dài nhất, không hết hạn sau 2 phút.
        Long ttl = redisTemplate.getExpire(RateLimitFilter.REGISTER_KEY_PREFIX + IP);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(120L);
        assertThat(register(OTHER_IP).getResponse().getStatus()).isEqualTo(400);
    }
}
