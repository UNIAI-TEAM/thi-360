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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String KEY_PREFIX = "rl:login:ip:";

    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;
    private final SecurityErrorWriter errorWriter;
    private final LettuceConnectionFactory lettuceConnectionFactory;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    public RateLimitFilter(
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties,
            SecurityErrorWriter errorWriter,
            LettuceConnectionFactory lettuceConnectionFactory) {
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory, "lettuceConnectionFactory must not be null");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = extractPath(request);
        return !LOGIN_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        int limit = Math.max(1, securityProperties.rateLimit().loginPerIpLimit());
        Duration window = securityProperties.rateLimit().loginPerIpWindow();

        ConsumptionProbe probe;
        try {
            ProxyManager<String> manager = getOrCreateProxyManager();
            Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(limit)
                            .refillIntervally(limit, window)
                            .build())
                    .build();
            Bucket bucket = manager.builder().build(KEY_PREFIX + clientIp, configSupplier);
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
                .addKeyValue("event", "rate_limit.login_ip_exceeded")
                .addKeyValue("clientIp", clientIp)
                .addKeyValue("limit", limit)
                .log("Per-IP login rate limit exceeded");
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
                Duration window = securityProperties.rateLimit().loginPerIpWindow();
                Duration maxTtl = window.multipliedBy(2);
                current = Bucket4jLettuce.casBasedBuilder(conn)
                        .expirationAfterWrite(
                                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(maxTtl))
                        .build();
                this.proxyManager = current;
            }
            return current;
        }
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
