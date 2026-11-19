package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import vn.unicom.thi360.shared.security.KeycloakProperties;

/**
 * Khởi động rút gọn: chỉ nạp {@code application.yml} theo profile và cấu hình của profile {@code e2e}
 * ({@link E2eConfig}). Đủ để kiểm chốt chặn, giá trị pepper và đồng hồ; không cần cơ sở dữ liệu.
 */
class E2eProfileGuardTests {

    private ApplicationContextRunner runnerForProfiles(String... profiles) {
        return runnerForProfiles(Map.of(), profiles);
    }

    /**
     * {@code systemEnvironment} và {@code systemProperties} của môi trường thử được thay bằng nội
     * dung do test kiểm soát: biến thật của máy chạy test (ví dụ {@code THI360_TOKEN_PEPPER}) không
     * ảnh hưởng kết quả. Cách đặt tên biến theo cùng quy tắc của biến môi trường thật.
     */
    private ApplicationContextRunner runnerForProfiles(Map<String, Object> environmentVariables, String... profiles) {
        return new ApplicationContextRunner()
                .withBean(KeycloakProperties.class, () -> new KeycloakProperties(
                        "http://localhost:8360", "thi-360", "thi360-api", "s", "master", "PLATFORM_ADMIN",
                        "thi360-platform", "s"))
                .withUserConfiguration(E2eConfig.class)
                .withInitializer(ctx -> {
                    MutablePropertySources sources = ctx.getEnvironment().getPropertySources();
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                    environmentVariables));
                    sources.replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of()));
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
        // Môi trường được cô lập: không có THI360_TOKEN_PEPPER, nên giá trị đến từ khối on-profile: e2e.
        runnerForProfiles("api", "worker", "e2e").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("thi360.token.pepper")).startsWith("dev-only-");
        });
    }

    /** Biến môi trường vẫn thắng giá trị dev-only (R1-11); không in giá trị khi lỗi. */
    @Test
    void pepperEnvironmentOverrideTakesPrecedenceOverTheDevOnlyDefault() {
        String synthetic = "synthetic-pepper-" + UUID.randomUUID();
        runnerForProfiles(Map.of("THI360_TOKEN_PEPPER", synthetic), "api", "worker", "e2e").run(context -> {
            assertThat(context).hasNotFailed();
            String actual = context.getEnvironment().getProperty("thi360.token.pepper");
            assertThat(synthetic.equals(actual)).as("environment override wins over the dev-only default").isTrue();
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
