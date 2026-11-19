package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * TM 10.2, TM 8.4, plan B6: per-IP limit of {@code GET /api/v1/public/**}. There is no public endpoint
 * before CM-1, so the real filter runs against real Redis with {@link MockHttpServletRequest}. Every case
 * uses its own IP range so no case shares a counter with another.
 */
class RateLimitPublicPolicyIT extends AbstractIntegrationTest {

    private static final String PUBLIC_PATH = "/api/v1/public/storefront/products";
    private static final String IPN_PATH = "/api/v1/public/payments/vnpay/ipn";
    private static final String RETURN_PATH = "/api/v1/public/payments/vnpay/return";

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

    private final List<RateLimitFilter> filters = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        for (String prefix : List.of(
                RateLimitFilter.PUBLIC_KEY_PREFIX, RateLimitFilter.KEY_PREFIX, RateLimitFilter.UPLOAD_KEY_PREFIX)) {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        filters.forEach(RateLimitFilter::close);
        filters.clear();
    }

    @Test
    void limitsAt121WithinWindow() throws Exception {
        String ip = "198.51.100.11";
        RateLimitFilter filter = publicFilter(defaults());

        for (int i = 1; i <= 120; i++) {
            assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).as("request %d", i).isEqualTo(200);
        }
        MockHttpServletResponse blocked = get(filter, PUBLIC_PATH, ip);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        // The Redis key must live at least as long as the window it counts.
        Long ttl = redisTemplate.getExpire(RateLimitFilter.PUBLIC_KEY_PREFIX + ip, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThanOrEqualTo(60L);
    }

    @Test
    void windowResetAllowsAgain() throws Exception {
        String ip = "198.51.100.21";
        RateLimitFilter filter = publicFilter(rateLimit(3, Duration.ofSeconds(2), List.of()));

        for (int i = 1; i <= 3; i++) {
            assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).isEqualTo(200);
        }
        assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).isEqualTo(429);

        Thread.sleep(2500);

        assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentRequestsNeverExceedLimit() throws Exception {
        String ip = "198.51.100.31";
        RateLimitFilter filter = publicFilter(defaults());
        int total = 130;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    int status = get(filter, PUBLIC_PATH, ip).getStatus();
                    (status == 200 ? passed : blocked).incrementAndGet();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(passed.get()).isEqualTo(120);
        assertThat(blocked.get()).isEqualTo(total - 120);
    }

    @Test
    void exemptIpIsNotLimited() throws Exception {
        RateLimitFilter filter = publicFilter(rateLimit(120, Duration.ofMinutes(1),
                List.of("198.51.100.40", "198.51.101.0/24")));

        for (String exempt : List.of("198.51.100.40", "198.51.101.77")) {
            for (int i = 1; i <= 130; i++) {
                assertThat(get(filter, PUBLIC_PATH, exempt).getStatus())
                        .as("%s request %d", exempt, i).isEqualTo(200);
            }
        }
        // Neighbours outside the list are still limited.
        for (String other : List.of("198.51.100.41", "198.51.102.77")) {
            for (int i = 1; i <= 120; i++) {
                assertThat(get(filter, PUBLIC_PATH, other).getStatus()).isEqualTo(200);
            }
            assertThat(get(filter, PUBLIC_PATH, other).getStatus()).as(other).isEqualTo(429);
        }
    }

    @Test
    void defaultListExemptsNobody() throws Exception {
        RateLimitFilter filter = publicFilter(defaults());

        // Not even loopback, the typical address of a local web server.
        for (String ip : List.of("198.51.100.50", "127.0.0.1")) {
            for (int i = 1; i <= 120; i++) {
                assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).isEqualTo(200);
            }
            assertThat(get(filter, PUBLIC_PATH, ip).getStatus()).as(ip).isEqualTo(429);
        }
    }

    @Test
    void clientBehindExemptProxyIsCountedByClientIp() throws Exception {
        String webServer = "172.20.0.10"; // inside the trusted proxy range 172.16.0.0/12
        String student = "203.0.113.150";
        RateLimitFilter filter = publicFilter(rateLimit(120, Duration.ofMinutes(1), List.of(webServer)));

        for (int i = 1; i <= 120; i++) {
            assertThat(getForwarded(filter, PUBLIC_PATH, webServer, student).getStatus()).isEqualTo(200);
        }
        assertThat(getForwarded(filter, PUBLIC_PATH, webServer, student).getStatus()).isEqualTo(429);
        // Another student behind the same web server has their own counter.
        assertThat(getForwarded(filter, PUBLIC_PATH, webServer, "203.0.113.151").getStatus()).isEqualTo(200);
        // The web server's own calls (server-side rendering, no forwarded client) stay exempt.
        for (int i = 1; i <= 130; i++) {
            assertThat(get(filter, PUBLIC_PATH, webServer).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void spoofedForwardedHeaderIsNotExempt() throws Exception {
        String exempt = "10.9.9.9";
        String attacker = "198.51.100.60"; // untrusted peer
        RateLimitFilter filter = publicFilter(rateLimit(120, Duration.ofMinutes(1), List.of(exempt)));

        for (int i = 1; i <= 120; i++) {
            assertThat(getForwarded(filter, PUBLIC_PATH, attacker, exempt).getStatus()).isEqualTo(200);
        }
        assertThat(getForwarded(filter, PUBLIC_PATH, attacker, exempt).getStatus()).isEqualTo(429);
    }

    @Test
    void exemptIpStaysLimitedOnLoginAndUpload() throws Exception {
        String ip = "198.51.100.70";
        RateLimitFilter loginFilter = publicFilter(rateLimit(120, Duration.ofMinutes(1), List.of(ip)));
        RateLimitFilter uploadFilter = filter(false, rateLimit(120, Duration.ofMinutes(1), List.of(ip)));

        for (int i = 1; i <= 20; i++) {
            assertThat(request(loginFilter, "POST", "/api/v1/auth/login", ip).getStatus()).isEqualTo(200);
        }
        assertThat(request(loginFilter, "POST", "/api/v1/auth/login", ip).getStatus()).isEqualTo(429);

        for (int i = 1; i <= 10; i++) {
            assertThat(request(uploadFilter, "POST", "/api/v1/admin/import-batches", ip).getStatus())
                    .isEqualTo(200);
        }
        assertThat(request(uploadFilter, "POST", "/api/v1/admin/import-batches", ip).getStatus())
                .isEqualTo(429);
    }

    @Test
    void vnpayIpnIsNeverLimited() throws Exception {
        String ip = "198.51.100.80";
        RateLimitFilter filter = publicFilter(defaults());

        for (int i = 1; i <= 150; i++) {
            assertThat(get(filter, IPN_PATH, ip).getStatus()).as("ipn %d", i).isEqualTo(200);
            assertThat(get(filter, IPN_PATH + "/", ip).getStatus()).isEqualTo(200);
        }
        // The IPN calls did not eat the budget of the other public endpoints, and the return page
        // (same prefix, different path) is limited like any other public GET.
        assertThat(redisTemplate.hasKey(RateLimitFilter.PUBLIC_KEY_PREFIX + ip)).isFalse();
        for (int i = 1; i <= 120; i++) {
            assertThat(get(filter, RETURN_PATH, ip).getStatus()).isEqualTo(200);
        }
        assertThat(get(filter, RETURN_PATH, ip).getStatus()).isEqualTo(429);
    }

    @Test
    void headSharesTheBucketAndGeneralFilterIgnoresPublicGet() throws Exception {
        String ip = "198.51.100.90";
        RateLimitFilter filter = publicFilter(rateLimit(2, Duration.ofMinutes(1), List.of()));
        RateLimitFilter generalFilter = filter(false, defaults());

        assertThat(request(filter, "GET", PUBLIC_PATH, ip).getStatus()).isEqualTo(200);
        assertThat(request(filter, "HEAD", PUBLIC_PATH, ip).getStatus()).isEqualTo(200);
        assertThat(request(filter, "HEAD", PUBLIC_PATH, ip).getStatus()).isEqualTo(429);

        // The second filter instance of the chain must not count the same request again.
        String other = "198.51.100.91";
        for (int i = 1; i <= 130; i++) {
            assertThat(request(generalFilter, "GET", PUBLIC_PATH, other).getStatus()).isEqualTo(200);
        }
        assertThat(redisTemplate.hasKey(RateLimitFilter.PUBLIC_KEY_PREFIX + other)).isFalse();
        // Non-public GET paths are out of scope of this policy.
        for (int i = 1; i <= 130; i++) {
            assertThat(request(filter, "GET", "/api/v1/me", other).getStatus()).isEqualTo(200);
        }
    }

    private SecurityProperties.RateLimitProperties defaults() {
        return securityProperties.rateLimit();
    }

    private SecurityProperties.RateLimitProperties rateLimit(int publicLimit, Duration window, List<String> exempt) {
        SecurityProperties.RateLimitProperties base = securityProperties.rateLimit();
        return new SecurityProperties.RateLimitProperties(
                base.loginPerIpLimit(), base.loginPerIpWindow(),
                base.uploadPerIpLimit(), base.uploadPerIpWindow(),
                publicLimit, window, exempt);
    }

    /** The instance in front of authentication: login and public GET. */
    private RateLimitFilter publicFilter(SecurityProperties.RateLimitProperties rateLimit) {
        return filter(true, rateLimit);
    }

    private RateLimitFilter filter(boolean loginOnly, SecurityProperties.RateLimitProperties rateLimit) {
        SecurityProperties properties = new SecurityProperties(
                securityProperties.adminOrigins(),
                securityProperties.trustedProxies(),
                rateLimit,
                securityProperties.loginThrottle(),
                securityProperties.session());
        RateLimitFilter filter =
                new RateLimitFilter(clientIpResolver, properties, errorWriter, lettuceConnectionFactory, loginOnly);
        filters.add(filter);
        return filter;
    }

    private MockHttpServletResponse get(RateLimitFilter filter, String path, String remoteAddr) throws Exception {
        return request(filter, "GET", path, remoteAddr);
    }

    private MockHttpServletResponse request(RateLimitFilter filter, String method, String path, String remoteAddr)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return run(filter, request);
    }

    private MockHttpServletResponse getForwarded(
            RateLimitFilter filter, String path, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, forwardedFor);
        return run(filter, request);
    }

    private static MockHttpServletResponse run(RateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
