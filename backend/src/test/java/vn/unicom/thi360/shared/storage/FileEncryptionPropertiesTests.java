package vn.unicom.thi360.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FileEncryptionPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FileEncryptionProperties.class)
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
    void failsStartupOutsideDevAndTestWhenEncKeyMissingOrInvalid() {
        runnerForProfiles("api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("thi360.file");
                });

        runnerForProfiles()
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("thi360.file");
                });

        runnerForProfiles("api")
                .withPropertyValues("THI360_FILE_ENC_KEY=not-valid-base64-length")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("thi360.file");
                });

        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        runnerForProfiles("api")
                .withPropertyValues("THI360_FILE_ENC_KEY=" + shortKey)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("thi360.file");
                });
    }

    @Test
    void succeedsOnDevAndTestProfilesWithDefaultKey() {
        runnerForProfiles("dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileEncryptionProperties.class).isEncKeyValid()).isTrue();
                });

        runnerForProfiles("test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileEncryptionProperties.class).isEncKeyValid()).isTrue();
                });
    }

    @Test
    void succeedsOutsideDevWhenValid32ByteBase64KeyProvided() {
        String validKey = Base64.getEncoder().encodeToString(new byte[32]);
        runnerForProfiles("api")
                .withPropertyValues("THI360_FILE_ENC_KEY=" + validKey)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileEncryptionProperties.class).encKey()).isEqualTo(validKey);
                });
    }
}
