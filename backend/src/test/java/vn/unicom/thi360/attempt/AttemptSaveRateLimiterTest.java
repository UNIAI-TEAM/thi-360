package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.lettuce.core.AbstractRedisClient;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import vn.unicom.thi360.attempt.application.AttemptSaveRateLimiter;

/** Verifies {@link AttemptSaveRateLimiter} fails open with a WARN on Redis outage (AC-9). */
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
}
