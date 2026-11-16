package vn.unicom.thi360.questionbank.api;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.unicom.thi360.questionbank.application.QuestionAssetProperties;
import vn.unicom.thi360.shared.api.RateLimitedException;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Giới hạn tải lên tài nguyên <b>theo người dùng</b>: 60 tệp mỗi 10 phút cho
 * {@code POST /api/v1/admin/question-assets} (spec 4.2; quyết định lane 8.1 — bộ lọc
 * chung {@code RateLimitFilter} là per-IP và thuộc {@code shared/**}, nên module tự có
 * interceptor này). Bucket bucket4j trên Redis (key {@code rl:qasset:user:<id>}), lỗi Redis
 * fail-open như bộ lọc chung; quá hạn trả {@link RateLimitedException} → 429.
 */
public class QuestionAssetUploadRateLimiter implements HandlerInterceptor {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    public static final String KEY_PREFIX = "rl:qasset:user:";

    private final QuestionAssetProperties properties;
    private final CurrentUserProvider currentUserProvider;
    private final LettuceConnectionFactory lettuceConnectionFactory;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    public QuestionAssetUploadRateLimiter(
            QuestionAssetProperties properties,
            CurrentUserProvider currentUserProvider,
            LettuceConnectionFactory lettuceConnectionFactory) {
        this.properties = Objects.requireNonNull(properties);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        CurrentUser user = currentUserProvider.getCurrentUser().orElse(null);
        if (user == null) {
            // Chưa xác thực: tầng bảo mật trả 401; không tính bucket.
            return true;
        }
        int limit = Math.max(1, properties.uploadPerUserLimit());
        ConsumptionProbe probe;
        try {
            Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(limit)
                            .refillIntervally(limit, properties.uploadPerUserWindow())
                            .build())
                    .build();
            Bucket bucket = getOrCreateProxyManager().builder()
                    .build(KEY_PREFIX + user.id(), configSupplier);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            securityLog.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "rate_limit.backend_error")
                    .addKeyValue("userId", user.id())
                    .log("Redis rate-limit check failed; failing open");
            return true;
        }
        response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            return true;
        }
        long retryAfterSeconds = Math.max(1L,
                TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1L);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        securityLog.atWarn()
                .addKeyValue("event", "rate_limit.question_asset_upload_exceeded")
                .addKeyValue("userId", user.id())
                .addKeyValue("limit", limit)
                .log("Per-user question-asset upload rate limit exceeded");
        throw new RateLimitedException("RATE_LIMITED");
    }

    public void close() {
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        if (conn != null) {
            conn.close();
        }
    }

    private ProxyManager<String> getOrCreateProxyManager() {
        ProxyManager<String> current = this.proxyManager;
        if (current == null) {
            synchronized (this) {
                current = this.proxyManager;
                if (current == null) {
                    Object nativeClient = lettuceConnectionFactory.getRequiredNativeClient();
                    if (!(nativeClient instanceof RedisClient redisClient)) {
                        throw new IllegalStateException(
                                "Expected standalone Lettuce RedisClient, got: "
                                        + nativeClient.getClass().getName());
                    }
                    StatefulRedisConnection<String, byte[]> conn = redisClient.connect(
                            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    this.connection = conn;
                    Duration window = properties.uploadPerUserWindow();
                    current = Bucket4jLettuce.casBasedBuilder(conn)
                            .expirationAfterWrite(ExpirationAfterWriteStrategy
                                    .basedOnTimeForRefillingBucketUpToMax(window.multipliedBy(2)))
                            .build();
                    this.proxyManager = current;
                }
            }
        }
        return current;
    }
}
