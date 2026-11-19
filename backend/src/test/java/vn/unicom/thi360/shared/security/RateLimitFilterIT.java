package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.unicom.thi360.AbstractIntegrationTest;

class RateLimitFilterIT extends AbstractIntegrationTest {

    private static final String UNTRUSTED_PEER = "198.51.100.10";
    private static final String CLIENT_A = "203.0.113.10";
    private static final String CLIENT_B = "203.0.113.11";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + UNTRUSTED_PEER,
                RateLimitFilter.KEY_PREFIX + CLIENT_A,
                RateLimitFilter.KEY_PREFIX + CLIENT_B));
    }

    @Test
    void ignoresSpoofedXffFromUntrustedPeer_andTripsAt21stAttemptOnRemoteAddr() throws Exception {
        String untrustedPeer = UNTRUSTED_PEER;
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(untrustedPeer);
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(untrustedPeer);
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.250")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void usesUntrustedClientAddressFromTrustedPeer_andIsolatesDifferentClients() throws Exception {
        String clientA = CLIENT_A;
        String clientB = CLIENT_B;
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr("127.0.0.1");
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, clientA + ", 172.18.0.2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientA + ", 172.18.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientB + ", 172.18.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
}

class RegistrationRateLimitWindowsIT extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final Instant T0 = Instant.parse("2026-11-19T00:00:00Z");

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private SecurityErrorWriter errorWriter;

    @Autowired
    private LettuceConnectionFactory lettuceConnectionFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final SettableClock clock = new SettableClock(T0);
    private final List<RateLimitFilter> filters = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        for (String prefix : List.of(
                RateLimitFilter.REGISTER_KEY_PREFIX, RateLimitFilter.KEY_PREFIX, RateLimitFilter.PUBLIC_KEY_PREFIX)) {
            var keys = redisTemplate.keys(prefix + "198.51.100.*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        filters.forEach(RateLimitFilter::close);
        filters.clear();
    }

    /**
     * Cửa sổ ngày đếm lần nhận thật trong 24 giờ liền trước, không hồi cả lô: sau mốc 24 giờ chỉ những lần đã rời
     * cửa sổ mới trả lại lượt. (Bucket4j {@code refillIntervally} cho 100 lượt cùng lúc ở mốc đó.)
     */
    @Test
    void dailyLimitIsARollingWindow() throws Exception {
        String ip = "198.51.100.11";
        RateLimitFilter filter = registrationFilter();

        // 100 lần trong 3 giờ đầu, trong giới hạn 30 mỗi giờ: 30 + 30 + 30 + 10.
        clock.set(T0);
        accept(filter, ip, 30);
        long hourWait = retryAfter(filter, ip);
        assertThat(hourWait).as("blocked by the hour window until the first batch leaves it").isEqualTo(3600L);

        clock.set(T0.plus(Duration.ofMinutes(61)));
        accept(filter, ip, 30);
        clock.set(T0.plus(Duration.ofMinutes(122)));
        accept(filter, ip, 30);
        clock.set(T0.plus(Duration.ofMinutes(183)));
        accept(filter, ip, 10);

        // Lần 101 trong 24 giờ: cửa sổ giờ còn chỗ (chỉ 10 lần trong giờ qua) nên chặn là do cửa sổ ngày, phải chờ tới
        // khi lần cũ nhất (T0) rời cửa sổ.
        long dayWait = retryAfter(filter, ip);
        assertThat(dayWait).isEqualTo(Duration.ofHours(24).minus(Duration.ofMinutes(183)).toSeconds());

        // Một giây trước mốc 24 giờ vẫn bị chặn, và một lần bị chặn không kéo dài thời gian chờ.
        clock.set(T0.plus(Duration.ofHours(24)).minusSeconds(1));
        assertThat(retryAfter(filter, ip)).isEqualTo(1L);

        // Sau mốc 24 giờ chỉ 30 lần của đợt đầu rời cửa sổ: đúng 30 lượt được trả lại, không phải 100.
        clock.set(T0.plus(Duration.ofHours(24)).plusSeconds(1));
        accept(filter, ip, 30);
        // Đợt thứ hai (T0+61 phút) còn nằm trong cửa sổ ngày: lần kế phải chờ tới khi nó rời đi.
        assertThat(retryAfter(filter, ip)).isEqualTo(Duration.ofMinutes(61).minusSeconds(1).toSeconds());
    }

    /** E19: khóa Redis sống theo cửa sổ dài nhất; lần bị từ chối không ghi gì thêm. */
    @Test
    void keyLivesAsLongAsTheLongestWindowAndDeniedRequestsAreNotRecorded() throws Exception {
        String ip = "198.51.100.21";
        RateLimitFilter filter = registrationFilter();

        accept(filter, ip, 30);
        retryAfter(filter, ip);

        Long ttl = redisTemplate.getExpire(RateLimitFilter.REGISTER_KEY_PREFIX + ip, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(23L * 3600, 24L * 3600);
        Long recorded = redisTemplate.opsForZSet().zCard(RateLimitFilter.REGISTER_KEY_PREFIX + ip);
        assertThat(recorded).isEqualTo(30L);
    }

    /** Quyết định trên cả hai cửa sổ là nguyên tử: 50 yêu cầu đồng thời của một IP chỉ nhận đúng 30. */
    @Test
    void concurrentRequestsNeverExceedTheHourLimit() throws Exception {
        String ip = "198.51.100.31";
        RateLimitFilter filter = registrationFilter();
        int total = 50;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return register(filter, ip).getStatus();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int ok = 0;
            int limited = 0;
            for (Future<Integer> future : futures) {
                int status = future.get(60, TimeUnit.SECONDS);
                if (status == 200) {
                    ok++;
                } else if (status == 429) {
                    limited++;
                }
            }
            assertThat(ok).isEqualTo(30);
            assertThat(limited).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * R1-04: chính sách đăng ký không đổi cách giữ khóa của đăng nhập và API công khai: khóa của chúng vẫn chỉ sống
     * cỡ cửa sổ của chúng cộng biên hai cửa sổ dài nhất của chính chúng, không phải biên 48 giờ của cửa sổ ngày.
     */
    @Test
    void otherPoliciesKeepTheirShortKeyLifetime() throws Exception {
        String ip = "198.51.100.41";
        SecurityProperties.RateLimitProperties rateLimit = securityProperties.rateLimit();
        Duration longestLegacy = Stream.of(
                        rateLimit.loginPerIpWindow(), rateLimit.uploadPerIpWindow(), rateLimit.publicPerIpWindow())
                .max(Duration::compareTo)
                .orElseThrow();
        RateLimitFilter filter = registrationFilter();

        assertThat(post(filter, "/api/v1/auth/login", ip).getStatus()).isEqualTo(200);
        assertThat(get(filter, "/api/v1/public/storefront/products", ip).getStatus()).isEqualTo(200);

        long bound = rateLimit.loginPerIpWindow().plus(longestLegacy.multipliedBy(2)).getSeconds();
        assertThat(bound).as("legacy bound is minutes, not days").isLessThan(3600L);
        for (String prefix : List.of(RateLimitFilter.KEY_PREFIX, RateLimitFilter.PUBLIC_KEY_PREFIX)) {
            Long ttl = redisTemplate.getExpire(prefix + ip, TimeUnit.SECONDS);
            assertThat(ttl).as(prefix).isNotNull().isPositive().isLessThanOrEqualTo(bound);
        }
    }

    private RateLimitFilter registrationFilter() {
        RateLimitFilter filter =
                new RateLimitFilter(clientIpResolver, securityProperties, errorWriter, lettuceConnectionFactory, true, clock);
        filters.add(filter);
        return filter;
    }

    private void accept(RateLimitFilter filter, String ip, int count) throws Exception {
        for (int i = 1; i <= count; i++) {
            assertThat(register(filter, ip).getStatus()).as("request %d at %s", i, clock.instant()).isEqualTo(200);
        }
    }

    /** Gửi một lần đăng ký bị chặn và trả {@code Retry-After}. */
    private long retryAfter(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletResponse response = register(filter, ip);
        assertThat(response.getStatus()).as("blocked at %s", clock.instant()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        return Long.parseLong(response.getHeader("Retry-After"));
    }

    private MockHttpServletResponse register(RateLimitFilter filter, String ip) throws Exception {
        return post(filter, REGISTER, ip);
    }

    private MockHttpServletResponse post(RateLimitFilter filter, String path, String ip) throws Exception {
        return run(filter, "POST", path, ip);
    }

    private MockHttpServletResponse get(RateLimitFilter filter, String path, String ip) throws Exception {
        return run(filter, "GET", path, ip);
    }

    private static MockHttpServletResponse run(RateLimitFilter filter, String method, String path, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class SettableClock extends Clock {
        private volatile Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
