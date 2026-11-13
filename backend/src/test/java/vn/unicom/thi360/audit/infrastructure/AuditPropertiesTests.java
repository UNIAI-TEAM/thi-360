package vn.unicom.thi360.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuditPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuditProperties.class)
    static class TestConfig {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class)
                .withInitializer(ctx -> ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment()));
    }

    @Test
    void defaultsToEightQuartersFromApplicationYml() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AuditProperties.class).archiveAfterQuarters()).isEqualTo(8);
        });
    }

    @Test
    void acceptsZeroQuarters() {
        runner()
                .withPropertyValues("thi360.audit.archive-after-quarters=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AuditProperties.class).archiveAfterQuarters()).isZero();
                });
    }

    @Test
    void rejectsNegativeArchiveAfterQuarters() {
        runner()
                .withPropertyValues("THI360_AUDIT_ARCHIVE_AFTER_QUARTERS=-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.audit")
                            .hasStackTraceContaining("archiveAfterQuarters");
                });
    }
}
