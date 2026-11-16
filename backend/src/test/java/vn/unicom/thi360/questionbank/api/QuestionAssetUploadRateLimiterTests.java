package vn.unicom.thi360.questionbank.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import vn.unicom.thi360.questionbank.application.QuestionAssetProperties;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Bucket Redis lỗi → fail-open như bộ lọc chung {@code RateLimitFilter} (quyết định của
 * lead trong vòng sửa 1): request đi tiếp (các giới hạn cỡ/loại tệp vẫn chặn sau) và một
 * WARN {@code rate_limit.backend_error} với đúng câu log của bộ lọc chung.
 */
class QuestionAssetUploadRateLimiterTests {

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
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.getCurrentUser()).thenReturn(Optional.of(new CurrentUser(
                UUID.randomUUID(), "admin.user", Realm.APP, Access.MANAGE,
                PermissionSet.empty())));
        LettuceConnectionFactory lettuce = new LettuceConnectionFactory() {
            @Override
            public io.lettuce.core.AbstractRedisClient getRequiredNativeClient() {
                throw new RedisConnectionFailureException("simulated Redis outage");
            }
        };
        QuestionAssetUploadRateLimiter limiter = new QuestionAssetUploadRateLimiter(
                new QuestionAssetProperties(
                        DataSize.ofMegabytes(5), DataSize.ofMegabytes(20),
                        60, Duration.ofMinutes(10), "questions/"),
                users, lettuce);

        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/question-assets");
        HttpServletResponse response = new MockHttpServletResponse();
        assertThat(limiter.preHandle(request, response, new Object())).isTrue();
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Redis rate-limit check failed; failing open");
        });
    }
}
