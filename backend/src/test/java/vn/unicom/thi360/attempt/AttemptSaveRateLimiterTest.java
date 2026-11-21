package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import vn.unicom.thi360.attempt.application.AttemptSaveRateLimiter;

/** Verifies {@link AttemptSaveRateLimiter} fails open with a WARN on Redis outage, validates config, rebuilds closed connections, and rounds Retry-After with ceiling division (AC-9). */
class AttemptSaveRateLimiterTest {

    private final Logger securityLog = (Logger) LoggerFactory.getLogger("thi360.security");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        securityLog.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        securityLog.detachAppender(appender);
        appender.stop();
    }

    @Test
    void backendBucketFailureFailsOpenWithWarn() {
        LettuceConnectionFactory failingLettuce =
                new LettuceConnectionFactory() {
                    @Override
                    public AbstractRedisClient getRequiredNativeClient() {
                        throw new RedisConnectionFailureException("simulated Redis outage");
                    }
                };
        AttemptSaveRateLimiter limiter = new AttemptSaveRateLimiter(failingLettuce);

        assertThatCode(() -> limiter.checkLimit(UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(appender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.WARN);
                            assertThat(event.getFormattedMessage())
                                    .isEqualTo("Redis rate-limit check failed; failing open");
                        });
    }

    @Test
    void validatesPositiveWindowAndComputesCeilingRetryAfter() {
        LettuceConnectionFactory lettuce = mock(LettuceConnectionFactory.class);
        assertThatThrownBy(() -> new AttemptSaveRateLimiter(lettuce, 20, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttemptSaveRateLimiter(lettuce, 20, Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(AttemptSaveRateLimiter.toRetryAfterSeconds(0L)).isEqualTo(1L);
        assertThat(AttemptSaveRateLimiter.toRetryAfterSeconds(1L)).isEqualTo(1L);
        assertThat(AttemptSaveRateLimiter.toRetryAfterSeconds(1_000_000_000L)).isEqualTo(1L);
        assertThat(AttemptSaveRateLimiter.toRetryAfterSeconds(1_000_000_001L)).isEqualTo(2L);
        assertThat(AttemptSaveRateLimiter.toRetryAfterSeconds(10_000_000_000L)).isEqualTo(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closesPriorConnectionWhenClosedAndClosesNewConnectionOnBuilderFailure() {
        LettuceConnectionFactory lettuce = mock(LettuceConnectionFactory.class);
        RedisClient redisClient = mock(RedisClient.class);
        StatefulRedisConnection<String, byte[]> closedPriorConn = mock(StatefulRedisConnection.class);
        StatefulRedisConnection<String, byte[]> newConn = mock(StatefulRedisConnection.class);
        when(closedPriorConn.isOpen()).thenReturn(false);
        when(lettuce.getRequiredNativeClient()).thenReturn(redisClient);
        when(redisClient.connect(any(RedisCodec.class))).thenReturn(newConn);
        // Simulate failure inside Bucket4jLettuce.casBasedBuilder(newConn) when connection yields null resources
        when(newConn.isOpen()).thenReturn(true);

        AttemptSaveRateLimiter limiter = new AttemptSaveRateLimiter(lettuce, 20, Duration.ofSeconds(10));
        ReflectionTestUtils.setField(limiter, "connection", closedPriorConn);
        ReflectionTestUtils.setField(limiter, "proxyManager", mock(io.github.bucket4j.distributed.proxy.ProxyManager.class));

        assertThatCode(() -> limiter.checkLimit(UUID.randomUUID())).doesNotThrowAnyException();
        verify(closedPriorConn, times(1)).close();
        verify(newConn, times(1)).close();
        assertThat(ReflectionTestUtils.getField(limiter, "connection")).isNull();
        assertThat(ReflectionTestUtils.getField(limiter, "proxyManager")).isNull();
    }
}
