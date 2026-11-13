package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CtxPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CtxProperties.class)
    static class TestConfig {
    }

    private ApplicationContextRunner runnerForProfiles(String... profiles) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class)
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles(profiles);
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                });
    }

    @Test
    void failsStartupOnNonDevAndNonTestProfileWhenSecretIsMissingOrShort() {
        runnerForProfiles("api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.ctx");
                });

        runnerForProfiles()
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.ctx");
                });

        runnerForProfiles("api")
                .withPropertyValues("THI360_CTX_SECRET=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.ctx");
                });

        runnerForProfiles("api")
                .withPropertyValues("THI360_CTX_SECRET=" + "a".repeat(31))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.ctx");
                });

        runnerForProfiles("api")
                .withPropertyValues("THI360_CTX_SECRET=" + " ".repeat(32))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.ctx");
                });
    }

    @Test
    void succeedsOnDevAndTestProfilesWithDefaultSecret() {
        runnerForProfiles("dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CtxProperties.class).secret())
                            .isEqualTo("ctx-dev-only-change-me-32-bytes!!");
                });

        runnerForProfiles("test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CtxProperties.class).secret())
                            .isEqualTo("ctx-dev-only-change-me-32-bytes!!");
                });
    }

    @Test
    void succeedsOnNonDevProfileWhenSecretIsAtLeast32Utf8Bytes() {
        String validSecret = "prod-ctx-secret-key-at-least-32b!";
        runnerForProfiles("api")
                .withPropertyValues("THI360_CTX_SECRET=" + validSecret)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CtxProperties.class).secret()).isEqualTo(validSecret);
                });
    }
}
