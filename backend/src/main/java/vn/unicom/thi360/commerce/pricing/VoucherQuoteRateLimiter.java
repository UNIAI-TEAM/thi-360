package vn.unicom.thi360.commerce.pricing;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.pricing.CommercePricingProperties.VoucherProperties;
import vn.unicom.thi360.shared.api.RateLimitedException;

/**
 * Per-user voucher quote rate limiter using atomic Redis rolling windows on {@code rl:voucher:user:<userId>}
 * (TM 7.2: 10 requests/minute and 50 requests/24 hours).
 *
 * <p>Only invoked for requests with a non-blank {@code voucherCode}, after learner authentication/gate checks pass and
 * before voucher lookup, so invalid codes still count toward the user's quota while requests without a voucher never
 * consume quota (AC-37, AC-38). Fails open on Redis runtime errors and respects the injected {@link Clock}.
 */
@Component
public class VoucherQuoteRateLimiter {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    public static final String KEY_PREFIX = "rl:voucher:user:";

    private static final String ROLLING_WINDOW_SCRIPT = """
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

    private final CommercePricingProperties properties;
    private final LettuceConnectionFactory lettuceConnectionFactory;
    private final Clock clock;

    private volatile StatefulRedisConnection<String, byte[]> connection;

    public VoucherQuoteRateLimiter(
            CommercePricingProperties properties,
            LettuceConnectionFactory lettuceConnectionFactory,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.lettuceConnectionFactory = Objects.requireNonNull(
                lettuceConnectionFactory, "lettuceConnectionFactory must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Consumes one voucher-quote attempt for {@code userId}, setting rate-limit headers or throwing 429. */
    public void checkAndConsume(UUID userId, HttpServletResponse response) {
        Objects.requireNonNull(userId, "userId must not be null");
        VoucherProperties voucher = properties.voucher();
        Decision decision;
        try {
            decision = tryAcquire(
                    getOrCreateConnection(),
                    KEY_PREFIX + userId,
                    List.of(
                            new Window(voucher.quotePerUserMinuteLimit(), voucher.quotePerUserMinuteWindow()),
                            new Window(voucher.quotePerUserDayLimit(), voucher.quotePerUserDayWindow())),
                    clock.millis());
        } catch (RuntimeException ex) {
            securityLog.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "rate_limit.backend_error")
                    .addKeyValue("userId", userId)
                    .log("Redis voucher rate-limit check failed; failing open");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (decision.allowed()) {
            return;
        }

        long retryAfterSeconds = Math.max(1L, (decision.retryAfterMillis() + 999L) / 1000L);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        securityLog.atWarn()
                .addKeyValue("event", "rate_limit.voucher_quote_exceeded")
                .addKeyValue("userId", userId)
                .addKeyValue("minuteLimit", voucher.quotePerUserMinuteLimit())
                .addKeyValue("dayLimit", voucher.quotePerUserDayLimit())
                .log("Per-user voucher quote rate limit exceeded");
        throw new RateLimitedException("RATE_LIMITED");
    }

    @PreDestroy
    public void close() {
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        if (conn != null) {
            conn.close();
            this.connection = null;
        }
    }

    private StatefulRedisConnection<String, byte[]> getOrCreateConnection() {
        StatefulRedisConnection<String, byte[]> current = this.connection;
        if (current != null && current.isOpen()) {
            return current;
        }
        synchronized (this) {
            current = this.connection;
            if (current == null || !current.isOpen()) {
                Object nativeClient = lettuceConnectionFactory.getRequiredNativeClient();
                if (!(nativeClient instanceof RedisClient redisClient)) {
                    throw new IllegalStateException(
                            "Expected standalone Lettuce RedisClient, got: " + nativeClient.getClass().getName());
                }
                current = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                this.connection = current;
            }
            return current;
        }
    }

    private static Decision tryAcquire(
            StatefulRedisConnection<String, byte[]> connection,
            String key,
            List<Window> windows,
            long nowMillis) {
        byte[][] argv = new byte[2 + 2 * windows.size()][];
        argv[0] = Long.toString(nowMillis).getBytes(StandardCharsets.UTF_8);
        argv[1] = (nowMillis + "-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < windows.size(); i++) {
            Window window = windows.get(i);
            argv[2 + 2 * i] = Long.toString(window.duration().toMillis()).getBytes(StandardCharsets.UTF_8);
            argv[3 + 2 * i] = Integer.toString(Math.max(1, window.limit())).getBytes(StandardCharsets.UTF_8);
        }
        List<?> result = connection.sync().eval(ROLLING_WINDOW_SCRIPT, ScriptOutputType.MULTI, new String[] {key}, argv);
        boolean allowed = ((Number) result.get(0)).longValue() == 1L;
        return new Decision(allowed, ((Number) result.get(1)).longValue(), ((Number) result.get(2)).longValue());
    }

    private record Window(int limit, Duration duration) {
    }

    private record Decision(boolean allowed, long retryAfterMillis, long remaining) {
    }
}
