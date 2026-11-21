package vn.unicom.thi360.attempt.application;

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
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Per-attempt rate limiter for {@code PUT /api/v1/me/attempts/{attemptId}/answers}: 20 requests per
 * 10 seconds keyed by {@code rl:attempt:save:<attemptId>} using Bucket4j + Lettuce Redis, failing
 * open on Redis runtime errors (LT §7.1, AC-9).
 */
@Component
public class AttemptSaveRateLimiter {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    public static final String KEY_PREFIX = "rl:attempt:save:";

    private final LettuceConnectionFactory lettuceConnectionFactory;
    private final int limit;
    private final Duration window;

    private record Holder(
            StatefulRedisConnection<String, byte[]> connection, ProxyManager<String> proxyManager) {}

    private volatile Holder holder;

    @Autowired
    public AttemptSaveRateLimiter(
            @Lazy LettuceConnectionFactory lettuceConnectionFactory,
            @Value("${thi360.attempt.save-rate-limit.limit:20}") int limit,
            @Value("${thi360.attempt.save-rate-limit.window:PT10S}") Duration window) {
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory);
        this.limit = Math.max(1, limit);
        this.window = Objects.requireNonNull(window, "window must not be null");
        if (this.window.isZero() || this.window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    public AttemptSaveRateLimiter(LettuceConnectionFactory lettuceConnectionFactory) {
        this(lettuceConnectionFactory, 20, Duration.ofSeconds(10));
    }

    /** Consumes one save token for {@code attemptId}, throwing {@link AttemptRateLimitedException} if exhausted. */
    public void checkLimit(UUID attemptId) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        ConsumptionProbe probe;
        try {
            Supplier<BucketConfiguration> configSupplier =
                    () ->
                            BucketConfiguration.builder()
                                    .addLimit(
                                            Bandwidth.builder()
                                                    .capacity(limit)
                                                    .refillIntervally(limit, window)
                                                    .build())
                                    .build();
            Bucket bucket =
                    getOrCreateProxyManager().builder().build(KEY_PREFIX + attemptId, configSupplier);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            securityLog
                    .atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "rate_limit.backend_error")
                    .addKeyValue("attemptId", attemptId)
                    .log("Redis rate-limit check failed; failing open");
            return;
        }
        if (probe.isConsumed()) {
            return;
        }
        long retryAfterSeconds = toRetryAfterSeconds(probe.getNanosToWaitForRefill());
        securityLog
                .atWarn()
                .addKeyValue("event", "rate_limit.attempt_save_exceeded")
                .addKeyValue("attemptId", attemptId)
                .addKeyValue("limit", limit)
                .log("Per-attempt answer save rate limit exceeded");
        throw new AttemptRateLimitedException(retryAfterSeconds);
    }

    public static long toRetryAfterSeconds(long nanosToWaitForRefill) {
        if (nanosToWaitForRefill <= 0L) {
            return 1L;
        }
        return (nanosToWaitForRefill - 1L) / 1_000_000_000L + 1L;
    }

    @PreDestroy
    public void close() {
        Holder current = this.holder;
        this.holder = null;
        if (current != null) {
            closeQuietly(current.connection());
        }
    }

    private ProxyManager<String> getOrCreateProxyManager() {
        Holder current = this.holder;
        if (current == null || !current.connection().isOpen()) {
            synchronized (this) {
                current = this.holder;
                if (current == null || !current.connection().isOpen()) {
                    if (current != null) {
                        closeQuietly(current.connection());
                        this.holder = null;
                    }
                    Object nativeClient = lettuceConnectionFactory.getRequiredNativeClient();
                    if (!(nativeClient instanceof RedisClient redisClient)) {
                        throw new IllegalStateException(
                                "Expected standalone Lettuce RedisClient, got: "
                                        + nativeClient.getClass().getName());
                    }
                    StatefulRedisConnection<String, byte[]> conn =
                            redisClient.connect(
                                    RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    try {
                        ProxyManager<String> pm =
                                Bucket4jLettuce.casBasedBuilder(conn)
                                        .expirationAfterWrite(
                                                ExpirationAfterWriteStrategy
                                                        .basedOnTimeForRefillingBucketUpToMax(
                                                                window.multipliedBy(2)))
                                        .build();
                        current = new Holder(conn, pm);
                        this.holder = current;
                    } catch (RuntimeException ex) {
                        closeQuietly(conn);
                        throw ex;
                    }
                }
            }
        }
        return current.proxyManager();
    }

    private static void closeQuietly(StatefulRedisConnection<String, byte[]> conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
