package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** AC-31 and the defaults of the public rate-limit policy (TM 10.2). */
class RateLimitExemptIpsPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void malformedEntryStopsStartup() {
        for (String bad : List.of("not-an-ip", "10.0.0.0/33", "10.0.0.1/abc", "999.1.1.1", "10.0.0.0/-1", "::1/129",
                // Request-style forms the trusted-proxy parser would normalise into a valid address.
                "10.0.0.1%eth0", "10.0.0.0%x/24", "fe80::1%eth0", "[::1]", "10.0.0.0/+8", "10.0.0.0/ 8",
                "10.0.0.5 x", "10")) {
            runner.withPropertyValues("thi360.security.rate-limit.exempt-ips[0]=" + bad)
                    .run(context -> {
                        assertThat(context).as("entry '%s'", bad).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("thi360.security.rate-limit.exempt-ips");
                    });
        }
        // One bad entry among good ones is enough.
        runner.withPropertyValues(
                        "thi360.security.rate-limit.exempt-ips[0]=10.0.0.5",
                        "thi360.security.rate-limit.exempt-ips[1]=10.0.0.0/8x")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void addressesAndCidrRangesAreAccepted() {
        runner.withPropertyValues("thi360.security.rate-limit.exempt-ips=10.0.0.5, 192.168.0.0/16 ,2001:db8::/32,::1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<String> exempt = context.getBean(SecurityProperties.class).rateLimit().exemptIps();
                    assertThat(exempt).hasSize(4);
                    ExemptIpMatcher matcher = new ExemptIpMatcher(exempt);
                    assertThat(matcher.matches("10.0.0.5")).isTrue();
                    assertThat(matcher.matches("10.0.0.6")).isFalse();
                    assertThat(matcher.matches("192.168.200.1")).isTrue();
                    assertThat(matcher.matches("192.169.0.1")).isFalse();
                    assertThat(matcher.matches("2001:db8:1::7")).isTrue();
                    assertThat(matcher.matches("2001:db9::1")).isFalse();
                });
    }

    @Test
    void defaultsAre120PerMinuteAndNobodyExempt() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            SecurityProperties.RateLimitProperties rateLimit = context.getBean(SecurityProperties.class).rateLimit();
            assertThat(rateLimit.publicPerIpLimit()).isEqualTo(120);
            assertThat(rateLimit.publicPerIpWindow()).isEqualTo(java.time.Duration.ofMinutes(1));
            assertThat(rateLimit.exemptIps()).isEmpty();
            assertThat(new ExemptIpMatcher(rateLimit.exemptIps()).matches("127.0.0.1")).isFalse();
        });
    }

    @Test
    void blankElementStopsStartup() {
        for (String blankList : List.of(
                "thi360.security.rate-limit.exempt-ips[0]=",
                "thi360.security.rate-limit.exempt-ips[0]=   ",
                "thi360.security.rate-limit.exempt-ips=10.0.0.5,",
                "thi360.security.rate-limit.exempt-ips=10.0.0.5,,10.0.0.6")) {
            runner.withPropertyValues(blankList)
                    .run(context -> assertThat(context).as(blankList).hasFailed());
        }
    }

    @Test
    void nonPositivePublicWindowStopsStartup() {
        for (String window : List.of("PT0S", "-PT1M", "0s", "-5s")) {
            runner.withPropertyValues("thi360.security.rate-limit.public-per-ip-window=" + window)
                    .run(context -> {
                        assertThat(context).as("window %s", window).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("public-per-ip-window");
                    });
        }
        runner.withPropertyValues("thi360.security.rate-limit.public-per-ip-window=PT1S")
                .run(context -> assertThat(context.getBean(SecurityProperties.class)
                        .rateLimit().publicPerIpWindow()).isEqualTo(java.time.Duration.ofSeconds(1)));
    }

    @Test
    void emptyEnvironmentValueMeansNobodyExempt() {
        withSystemEnvironment(Map.of("THI360_SECURITY_RATELIMIT_EXEMPTIPS", ""))
                .run(context -> assertThat(context.getBean(SecurityProperties.class).rateLimit().exemptIps())
                        .isEmpty());
    }

    /** The deployment name of {@code exempt-ips} (.env.example) must bind through the real environment source. */
    @Test
    void deploymentEnvironmentVariableNameBindsTheList() {
        withSystemEnvironment(Map.of("THI360_SECURITY_RATELIMIT_EXEMPTIPS", "10.0.0.5,192.168.0.0/16"))
                .run(context -> assertThat(context.getBean(SecurityProperties.class).rateLimit().exemptIps())
                        .containsExactly("10.0.0.5", "192.168.0.0/16"));
        withSystemEnvironment(Map.of("THI360_SECURITY_RATELIMIT_EXEMPTIPS", "10.0.0.5,not-an-ip"))
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner withSystemEnvironment(Map<String, Object> variables) {
        return runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables)));
    }
}
