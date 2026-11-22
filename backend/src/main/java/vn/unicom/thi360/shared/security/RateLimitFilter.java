package vn.unicom.thi360.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;
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
    // Separate namespace from the previous Bucket4j string values: this policy stores a sorted set.
    public static final String REGISTER_KEY_PREFIX = "rl:register:rolling:ip:";
    public static final String FORGOT_KEY_PREFIX = "rl:forgot:ip:";
    public static final String RESET_KEY_PREFIX = "rl:reset:ip:";
    private static final List<String> FORGOT_SEGMENTS = List.of("api", "v1", "auth", "password", "forgot");
    private static final List<String> RESET_SEGMENTS = List.of("api", "v1", "auth", "password", "reset");
    /** Decoded path segments (as Spring MVC matches them) of the public API prefix {@code /api/v1/public/**}. */
    private static final List<String> PUBLIC_SEGMENTS = List.of("api", "v1", "public");
    /** TM 8.4: VNPAY retries the IPN up to 10 times; limiting it would lose payments. */
    private static final List<String> VNPAY_IPN_SEGMENTS =
            List.of("api", "v1", "public", "payments", "vnpay", "ipn");

    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;
    private final SecurityErrorWriter errorWriter;
    private final LettuceConnectionFactory lettuceConnectionFactory;
    private final boolean loginOnly;
    private final Clock clock;
    private final ExemptIpMatcher exemptIpMatcher;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    public RateLimitFilter(
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties,
            SecurityErrorWriter errorWriter,
            LettuceConnectionFactory lettuceConnectionFactory, boolean loginOnly) {
        this(clientIpResolver, securityProperties, errorWriter, lettuceConnectionFactory, loginOnly, Clock.systemUTC());
    }

    /** {@code clock}: mốc thời gian của giới hạn đăng ký (cửa sổ trượt); kiểm thử đưa đồng hồ cố định vào. */
    RateLimitFilter(
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties,
            SecurityErrorWriter errorWriter,
            LettuceConnectionFactory lettuceConnectionFactory, boolean loginOnly, Clock clock) {
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory, "lettuceConnectionFactory must not be null");
        this.loginOnly = loginOnly;
        this.exemptIpMatcher = new ExemptIpMatcher(securityProperties.rateLimit().exemptIps());
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (isReadMethod(request.getMethod())) {
            // Giới hạn API công khai chạy ở bộ lọc đứng trước xác thực (cùng bộ lọc với đăng nhập), đúng một lần mỗi yêu cầu.
            return !loginOnly || publicPolicy(request) == null;
        }
        if (passwordResetPolicy(request) != null) {
            return !loginOnly;
        }
        String path = extractPath(request);
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (RegistrationRoute.matches(request)) {
            // Đăng ký chạy ở bộ lọc đứng trước xác thực, đúng một lần mỗi yêu cầu.
            return !loginOnly;
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
        if (RegistrationRoute.matches(request)) {
            limitRegistration(request, response, filterChain);
            return;
        }
        RateLimitPolicy policy = isReadMethod(request.getMethod())
                ? publicPolicy(request)
                : resolvePolicy(extractPath(request));
        if (policy == null && !isReadMethod(request.getMethod())) {
            policy = passwordResetPolicy(request);
        }
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

    /**
     * Đăng ký (E19): 30 lần mỗi giờ và 100 lần mỗi ngày theo từng IP, đều là cửa sổ trượt, quyết định nguyên tử trên cả hai
     * cửa sổ. Chỉ chạy ở bộ lọc đứng trước xác thực; chính sách khác không đi qua đây.
     */
    private void limitRegistration(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityProperties.RateLimitProperties rateLimit = securityProperties.rateLimit();
        String clientIp = clientIpResolver.resolve(request);
        RollingWindowLimiter.Decision decision;
        try {
            getOrCreateProxyManager(); // mở kết nối Redis dùng chung
            decision = RollingWindowLimiter.tryAcquire(
                    connection,
                    REGISTER_KEY_PREFIX + clientIp,
                    List.of(
                            new RollingWindowLimiter.Window(
                                    rateLimit.registerPerIpHourLimit(), rateLimit.registerPerIpHourWindow()),
                            new RollingWindowLimiter.Window(
                                    rateLimit.registerPerIpDayLimit(), rateLimit.registerPerIpDayWindow())),
                    clock.millis());
        } catch (RuntimeException ex) {
            securityLog.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "rate_limit.backend_error")
                    .addKeyValue("clientIp", clientIp)
                    .log("Redis rate-limit check failed; failing open");
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1L, (decision.retryAfterMillis() + 999L) / 1000L);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        securityLog.atWarn()
                .addKeyValue("event", "rate_limit.register_ip_exceeded")
                .addKeyValue("clientIp", clientIp)
                .log("Per-IP registration rate limit exceeded");
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
                                securityProperties.rateLimit().publicPerIpWindow(),
                                securityProperties.rateLimit().forgotPerIpWindow(),
                                securityProperties.rateLimit().resetPerIpWindow())
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

    private RateLimitPolicy publicPolicy(HttpServletRequest request) {
        List<String> segments = decodedSegments(request);
        if (segments.size() < PUBLIC_SEGMENTS.size()
                || !segments.subList(0, PUBLIC_SEGMENTS.size()).equals(PUBLIC_SEGMENTS)
                || VNPAY_IPN_SEGMENTS.equals(segments)) {
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

    /**
     * Segments of the path within the application exactly as Spring MVC matches handlers: percent-decoded,
     * path parameters ({@code ;x=y}) removed, empty trailing segment ignored. The raw
     * {@code getRequestURI()} differs for admitted equivalent forms such as {@code /api/v1/%70ublic/...},
     * which would reach the controller of a public path without being counted.
     */
    static List<String> decodedSegments(HttpServletRequest request) {
        boolean parsedHere = !ServletRequestPathUtils.hasParsedRequestPath(request);
        try {
            RequestPath requestPath = ServletRequestPathUtils.parseAndCache(request);
            List<String> segments = new ArrayList<>();
            for (PathContainer.Element element : requestPath.pathWithinApplication().elements()) {
                if (element instanceof PathContainer.PathSegment segment) {
                    segments.add(segment.valueToMatch());
                }
            }
            return segments;
        } finally {
            if (parsedHere) {
                ServletRequestPathUtils.clearParsedRequestPath(request);
            }
        }
    }

    /** HEAD chạy vào cùng bộ xử lý GET nên phải chịu cùng giới hạn. */
    private static boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    /**
     * Chính sách của {@code POST /auth/password/forgot|reset} (E16, TM 13), khớp theo các đoạn đường đã giải mã
     * như Spring MVC để dạng mã hóa phần trăm không lọt qua bộ đếm. Chạy ở bộ lọc đứng trước xác thực.
     */
    private RateLimitPolicy passwordResetPolicy(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        List<String> segments = decodedSegments(request);
        if (FORGOT_SEGMENTS.equals(segments)) {
            return new RateLimitPolicy(
                    FORGOT_KEY_PREFIX,
                    securityProperties.rateLimit().forgotPerIpLimit(),
                    securityProperties.rateLimit().forgotPerIpWindow(),
                    "rate_limit.forgot_ip_exceeded",
                    "Per-IP password forgot rate limit exceeded",
                    false);
        }
        if (RESET_SEGMENTS.equals(segments)) {
            return new RateLimitPolicy(
                    RESET_KEY_PREFIX,
                    securityProperties.rateLimit().resetPerIpLimit(),
                    securityProperties.rateLimit().resetPerIpWindow(),
                    "rate_limit.reset_ip_exceeded",
                    "Per-IP password reset rate limit exceeded",
                    false);
        }
        return null;
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

    /** POST route matched using the same decoded segments as MVC. */
    static final class RegistrationRoute {
        private static final List<String> START = List.of("api", "v1", "auth", "register");
        private static final List<String> RESEND = List.of("api", "v1", "auth", "register", "resend");
        private static final List<String> VERIFY = List.of("api", "v1", "auth", "register", "verify");

        private RegistrationRoute() {}

        static boolean matches(HttpServletRequest request) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            List<String> segments = decodedSegments(request);
            return START.equals(segments) || RESEND.equals(segments) || VERIFY.equals(segments);
        }
    }

    /** Atomic rolling-window decisions for registration, isolated from the existing Bucket4j policies. */
    private static final class RollingWindowLimiter {
        record Window(int limit, Duration duration) {}
        record Decision(boolean allowed, long retryAfterMillis, long remaining) {}

        private static final String SCRIPT = """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local member = ARGV[2]
                local count = (#ARGV - 2) / 2
                local longest = 0
                for i = 1, count do
                  local w = tonumber(ARGV[1 + 2 * i])
                  if w > longest then longest = w end
                end
                redis.call('ZREMRANGEBYSCORE', key, '-inf', string.format('%.0f', now - longest))
                local retry = 0
                local remaining = -1
                for i = 1, count do
                  local w = tonumber(ARGV[1 + 2 * i])
                  local l = tonumber(ARGV[2 + 2 * i])
                  local from = '(' .. string.format('%.0f', now - w)
                  local c = redis.call('ZCOUNT', key, from, '+inf')
                  if c >= l then
                    local oldest = redis.call('ZRANGEBYSCORE', key, from, '+inf', 'WITHSCORES', 'LIMIT', c - l, 1)
                    local wait = tonumber(oldest[2]) + w - now
                    if wait > retry then retry = wait end
                  end
                  local left = l - c - 1
                  if remaining < 0 or left < remaining then remaining = left end
                end
                if retry > 0 then
                  return {0, retry, 0}
                end
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, string.format('%.0f', longest))
                return {1, 0, remaining}
                """;

        private RollingWindowLimiter() {}

        static Decision tryAcquire(
                StatefulRedisConnection<String, byte[]> connection, String key, List<Window> windows, long nowMillis) {
            byte[][] argv = new byte[2 + 2 * windows.size()][];
            argv[0] = Long.toString(nowMillis).getBytes(StandardCharsets.UTF_8);
            argv[1] = (nowMillis + "-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < windows.size(); i++) {
                Window window = windows.get(i);
                argv[2 + 2 * i] = Long.toString(window.duration().toMillis()).getBytes(StandardCharsets.UTF_8);
                argv[3 + 2 * i] = Integer.toString(Math.max(1, window.limit())).getBytes(StandardCharsets.UTF_8);
            }
            List<?> result = connection.sync().eval(SCRIPT, ScriptOutputType.MULTI, new String[] {key}, argv);
            boolean allowed = ((Number) result.get(0)).longValue() == 1L;
            return new Decision(allowed, ((Number) result.get(1)).longValue(), ((Number) result.get(2)).longValue());
        }
    }
}
