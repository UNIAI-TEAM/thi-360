package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;

/**
 * Kiểm tra logback-spring.xml ở hai chế độ console: dạng chữ (dev, test) và JSON (môi trường triển khai).
 *
 * <p>Nạp thẳng hệ thống log của Spring Boot thay vì dựng ApplicationContext, và nạp lại cấu hình mặc định
 * sau mỗi test, để định dạng log của test này không rò sang các test khác trong cùng JVM.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingConfigTests {

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private void initLogging(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);   // nạp application.yml như khi ứng dụng chạy
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), null, null);
    }

    @AfterEach
    void restoreDefaultLogging() {
        MDC.clear();
        initLogging();
    }

    @Test
    void textConsole_showsTraceAndUserAtFixedPosition_orDashWhenAbsent(CapturedOutput output) {
        initLogging("test");
        Logger log = LoggerFactory.getLogger("thi360.security");

        log.info("no-context-line");
        MDC.put("traceId", "trace-text-1");
        MDC.put("userId", "user-7");
        log.warn("login-blocked-line");

        assertThat(output.getOut())
                .containsPattern("INFO\\s+\\[trace=- user=-\\] thi360\\.security\\s+: no-context-line")
                .containsPattern("WARN\\s+\\[trace=trace-text-1 user=user-7\\] thi360\\.security\\s+: login-blocked-line");
    }

    @Test
    void jsonConsole_carriesMdcKeys_serviceName_andKeyValues(CapturedOutput output) {
        initLogging("staging");   // bất kỳ profile nào khác dev, test, default
        Logger log = LoggerFactory.getLogger("thi360.access");

        MDC.put("traceId", "trace-json-1");
        MDC.put("userId", "user-42");
        log.atInfo().addKeyValue("status", 200).log("GET /api/v1/me");

        String line = output.getOut().lines()
                .filter(l -> l.contains("GET /api/v1/me"))
                .findFirst().orElseThrow();
        assertThat(line).startsWith("{").endsWith("}");
        assertThat(line)
                .contains("\"traceId\":\"trace-json-1\"")
                .contains("\"userId\":\"user-42\"")
                .contains("\"logger_name\":\"thi360.access\"")
                .contains("\"service\":\"thi360-api\"")
                .contains("\"status\"");
    }

    @Test
    void sqlLogging_isOffByDefault_andApplicationLoggerFollowsConfiguredLevel() {
        initLogging("test");

        assertThat(LoggerFactory.getLogger("org.hibernate.SQL").isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("vn.unicom.thi360.Anything").isInfoEnabled()).isTrue();
        assertThat(LoggerFactory.getLogger("vn.unicom.thi360.Anything").isDebugEnabled()).isFalse();
    }
}
