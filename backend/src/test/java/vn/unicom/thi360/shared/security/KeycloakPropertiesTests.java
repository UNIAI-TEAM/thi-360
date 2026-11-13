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

class KeycloakPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KeycloakProperties.class)
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
    void failsStartupOnNonDevAndNonTestProfileWhenRealmIsMissing() {
        runnerForProfiles("api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("thi360.keycloak")
                            .hasStackTraceContaining("realm");
                });
    }

    @Test
    void succeedsOnDevAndTestProfilesWithDefaultRealm() {
        runnerForProfiles("dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).realm()).isEqualTo("thi-360");
                });

        runnerForProfiles("test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).realm()).isEqualTo("thi-360");
                });
    }

    @Test
    void succeedsOnNonDevProfileWhenRealmIsProvided() {
        runnerForProfiles("api")
                .withPropertyValues("THI360_KC_REALM=thi-360-prod")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).realm()).isEqualTo("thi-360-prod");
                });
    }
}
