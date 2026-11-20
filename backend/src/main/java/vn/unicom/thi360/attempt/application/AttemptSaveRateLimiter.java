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
import java.util.concurrent.TimeUnit;
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

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    @Autowired
    public AttemptSaveRateLimiter(
            @Lazy LettuceConnectionFactory lettuceConnectionFactory,
            @Value("${thi360.attempt.save-rate-limit.limit:20}") int limit,
            @Value("${thi360.attempt.save-rate-limit.window:PT10S}") Duration window) {
        this.lettuceConnectionFactory = Objects.requireNonNull(lettuceConnectionFactory);
        this.limit = Math.max(1, limit);
        this.window = Objects.requireNonNull(window);
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
        long retryAfterSeconds =
                Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1L);
        securityLog
                .atWarn()
                .addKeyValue("event", "rate_limit.attempt_save_exceeded")
                .addKeyValue("attemptId", attemptId)
                .addKeyValue("limit", limit)
                .log("Per-attempt answer save rate limit exceeded");
        throw new AttemptRateLimitedException(retryAfterSeconds);
    }

    @PreDestroy
    public void close() {
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        this.proxyManager = null;
        this.connection = null;
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
                    StatefulRedisConnection<String, byte[]> conn =
                            redisClient.connect(
                                    RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    this.connection = conn;
                    current =
                            Bucket4jLettuce.casBasedBuilder(conn)
                                    .expirationAfterWrite(
                                            ExpirationAfterWriteStrategy
                                                    .basedOnTimeForRefillingBucketUpToMax(
                                                            window.multipliedBy(2)))
                                    .build();
                    this.proxyManager = current;
                }
            }
        }
        return current;
    }
}
