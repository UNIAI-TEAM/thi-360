package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.unicom.thi360.shared.security.KeycloakProperties;

/**
 * Khởi động rút gọn: chỉ nạp {@code application.yml} theo profile và cấu hình của profile {@code e2e}
 * ({@link E2eConfig}). Đủ để kiểm chốt chặn, giá trị pepper và đồng hồ; không cần cơ sở dữ liệu.
 */
class E2eProfileGuardTests {

    private ApplicationContextRunner runnerForProfiles(String... profiles) {
        return new ApplicationContextRunner()
                .withBean(KeycloakProperties.class, () -> new KeycloakProperties(
                        "http://localhost:8360", "thi-360", "thi360-api", "s", "master", "PLATFORM_ADMIN",
                        "thi360-platform", "s"))
                .withUserConfiguration(E2eConfig.class)
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles(profiles);
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                });
    }

    @Test
    void e2eWithProdFailsAndNamesBothProfiles() {
        runnerForProfiles("api", "worker", "e2e", "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("'e2e'")
                    .hasStackTraceContaining("'prod'");
        });
    }

    @Test
    void e2eWithProdFailsRegardlessOfProfileOrder() {
        runnerForProfiles("prod", "e2e").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void e2eAloneStartsWithDevOnlyPepper() {
        runnerForProfiles("api", "worker", "e2e").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("thi360.token.pepper")).startsWith("dev-only-");
        });
    }

    @Test
    void e2eProfileRegistersMutableClockAsTheClock() {
        runnerForProfiles("e2e").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Clock.class)).isInstanceOf(MutableClock.class);
        });
    }

    @Test
    void withoutE2eNothingIsRegisteredEvenWithProd() {
        runnerForProfiles("api", "prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MutableClock.class);
            assertThat(context).doesNotHaveBean(Clock.class);
        });
    }
}
