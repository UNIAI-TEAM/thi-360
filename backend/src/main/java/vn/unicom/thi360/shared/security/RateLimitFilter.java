package vn.unicom.thi360.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String IMPORT_UPLOAD_PATH = "/api/v1/admin/import-batches";
    private static final String QUESTION_IMPORT_UPLOAD_PATH = "/api/v1/admin/question-imports/uploads";
    public static final String KEY_PREFIX = "rl:login:ip:";
    public static final String UPLOAD_KEY_PREFIX = "rl:upload:ip:";
    public static final String PUBLIC_KEY_PREFIX = "rl:public:ip:";
    private static final String PUBLIC_PATH_PREFIX = "/api/v1/public/";
    /** TM 8.4: VNPAY retries the IPN up to 10 times; limiting it would lose payments. */
    private static final String VNPAY_IPN_PATH = "/api/v1/public/payments/vnpay/ipn";

    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;
    private final SecurityErrorWriter errorWriter;
    private final LettuceConnectionFactory lettuceConnectionFactory;
    private final boolean loginOnly;
    private final ExemptIpMatcher exemptIpMatcher;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    public RateLimitFilter(
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties,
            SecurityErrorWriter errorWriter,
            LettuceConnectionFactory lettuceConnectionFactory, boolean loginOnly) {
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory, "lettuceConnectionFactory must not be null");
        this.loginOnly = loginOnly;
        this.exemptIpMatcher = new ExemptIpMatcher(securityProperties.rateLimit().exemptIps());
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = extractPath(request);
        if (isReadMethod(request.getMethod())) {
            // Giới hạn API công khai chạy ở bộ lọc đứng trước xác thực (cùng bộ lọc với đăng nhập), đúng một lần mỗi yêu cầu.
            return !loginOnly || publicPolicy(path) == null;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return loginOnly ? !LOGIN_PATH.equals(path)
                : LOGIN_PATH.equals(path) || resolvePolicy(path) == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String path = extractPath(request);
        RateLimitPolicy policy = isReadMethod(request.getMethod()) ? publicPolicy(path) : resolvePolicy(path);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIpResolver.resolve(request);
        // Máy chủ web gọi từ IP cố định (trang dựng phía máy chủ) được miễn, nhưng chỉ chính sách công khai.
        // IP đã qua ClientIpResolver nên X-Forwarded-For giả từ máy không tin cậy không thể mượn IP được miễn;
        // học viên đi qua máy chủ web được miễn vẫn tính theo IP của chính họ.
        if (policy.exemptible() && exemptIpMatcher.matches(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }
        int limit = Math.max(1, policy.limit());
        Duration window = policy.window();

        ConsumptionProbe probe;
        try {
            ProxyManager<String> manager = getOrCreateProxyManager();
            Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(limit)
                            .refillIntervally(limit, window)
                            .build())
                    .build();
            Bucket bucket = manager.builder().build(policy.keyPrefix() + clientIp, configSupplier);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            securityLog.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "rate_limit.backend_error")
                    .addKeyValue("clientIp", clientIp)
                    .log("Redis rate-limit check failed; failing open");
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L,
                TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1L);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        securityLog.atWarn()
                .addKeyValue("event", policy.exceededEvent())
                .addKeyValue("clientIp", clientIp)
                .addKeyValue("limit", limit)
                .log(policy.exceededMessage());
        errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "RATE_LIMITED");
    }
    public void close() {
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        if (conn != null) {
            conn.close();
        }
    }

    private ProxyManager<String> getOrCreateProxyManager() {
        ProxyManager<String> current = this.proxyManager;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = this.proxyManager;
            if (current == null) {
                Object nativeClient = lettuceConnectionFactory.getRequiredNativeClient();
                if (!(nativeClient instanceof RedisClient redisClient)) {
                    throw new IllegalStateException("Expected standalone Lettuce RedisClient, got: " + nativeClient.getClass().getName());
                }
                StatefulRedisConnection<String, byte[]> conn =
                        redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                this.connection = conn;
                Duration maxWindow = Stream.of(
                                securityProperties.rateLimit().loginPerIpWindow(),
                                securityProperties.rateLimit().uploadPerIpWindow(),
                                securityProperties.rateLimit().publicPerIpWindow())
                        .max(Duration::compareTo)
                        .orElseThrow();
                Duration maxTtl = maxWindow.multipliedBy(2);
                current = Bucket4jLettuce.casBasedBuilder(conn)
                        .expirationAfterWrite(
                                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(maxTtl))
                        .build();
                this.proxyManager = current;
            }
            return current;
        }
    }

    private RateLimitPolicy publicPolicy(String path) {
        if (!path.startsWith(PUBLIC_PATH_PREFIX) || isVnpayIpn(path)) {
            return null;
        }
        return new RateLimitPolicy(
                PUBLIC_KEY_PREFIX,
                securityProperties.rateLimit().publicPerIpLimit(),
                securityProperties.rateLimit().publicPerIpWindow(),
                "rate_limit.public_ip_exceeded",
                "Per-IP public API rate limit exceeded",
                true);
    }

    private static boolean isVnpayIpn(String path) {
        return VNPAY_IPN_PATH.equals(path) || (VNPAY_IPN_PATH + "/").equals(path);
    }

    /** HEAD chạy vào cùng bộ xử lý GET nên phải chịu cùng giới hạn. */
    private static boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private RateLimitPolicy resolvePolicy(String path) {
        if (LOGIN_PATH.equals(path)) {
            return new RateLimitPolicy(
                    KEY_PREFIX,
                    securityProperties.rateLimit().loginPerIpLimit(),
                    securityProperties.rateLimit().loginPerIpWindow(),
                    "rate_limit.login_ip_exceeded",
                    "Per-IP login rate limit exceeded",
                    false);
        }
        if (IMPORT_UPLOAD_PATH.equals(path) || (IMPORT_UPLOAD_PATH + "/").equals(path)
                || QUESTION_IMPORT_UPLOAD_PATH.equals(path)
                || (QUESTION_IMPORT_UPLOAD_PATH + "/").equals(path)) {
            return new RateLimitPolicy(
                    UPLOAD_KEY_PREFIX,
                    securityProperties.rateLimit().uploadPerIpLimit(),
                    securityProperties.rateLimit().uploadPerIpWindow(),
                    "rate_limit.upload_ip_exceeded",
                    "Per-IP import upload rate limit exceeded",
                    false);
        }
        return null;
    }

    private record RateLimitPolicy(
            String keyPrefix,
            int limit,
            Duration window,
            String exceededEvent,
            String exceededMessage,
            boolean exemptible) {
    }
    private static String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
