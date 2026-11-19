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

class TokenPepperPropertiesTests {

    private static final String ENV_NAME = "THI360_TOKEN_PEPPER";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TokenPepperProperties.class)
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
    void missingPepperFailsStartup() {
        runnerForProfiles("api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.token.pepper");
                });

        runnerForProfiles()
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.token.pepper");
                });

        // Blank values are as bad as a missing value.
        runnerForProfiles("api")
                .withPropertyValues(ENV_NAME + "=" + " ".repeat(12))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.token.pepper");
                });
    }

    @Test
    void devAndTestUseDevOnlyValue() {
        for (String profile : new String[] {"dev", "test"}) {
            runnerForProfiles(profile)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(TokenPepperProperties.class).pepper())
                                .startsWith("dev-only-");
                    });
        }
    }

    @Test
    void environmentValueWinsAndIsAcceptedOnAnyProfile() {
        String provided = "provided-by-environment-" + "x".repeat(8);
        for (String profile : new String[] {"api", "dev", "test"}) {
            runnerForProfiles(profile)
                    .withPropertyValues(ENV_NAME + "=" + provided)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(TokenPepperProperties.class).pepper())
                                .isEqualTo(provided);
                    });
        }
    }
}
