package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayPageCondition;
import vn.unicom.thi360.commerce.payment.gateway.UnsupportedVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayConfiguration;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayMode;
import vn.unicom.thi360.commerce.payment.gateway.VnpayModeStartupGuard;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;

/**
 * AC-18: Kiểm tra đầy đủ bảng chân trị khởi động {@code thi360.vnpay.mode} và điều kiện mở trang {@code /mock-vnpay}.
 */
class VnpayModeStartupTest {

    @Test
    void rejectsMissingOrInvalidMode() {
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thi360.vnpay.mode");

        MockEnvironment blank = new MockEnvironment().withProperty("thi360.vnpay.mode", "   ");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(blank))
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment invalid = new MockEnvironment().withProperty("thi360.vnpay.mode", "STAGING");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STAGING");
    }

    @Test
    void allowsMockInDevTestE2eWithoutProd() {
        for (String profile : new String[] {"dev", "test", "e2e"}) {
            MockEnvironment env = new MockEnvironment()
                    .withProperty("thi360.vnpay.mode", "MOCK");
            env.setActiveProfiles(profile);
            assertThat(VnpayModeStartupGuard.validate(env)).isEqualTo(VnpayMode.MOCK);
            assertThat(conditionMatches(env)).isTrue();
        }
    }

    @Test
    void forbidsMockWithProdProfileAlways() {
        MockEnvironment prodOnly = new MockEnvironment().withProperty("thi360.vnpay.mode", "MOCK");
        prodOnly.setActiveProfiles("prod");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(prodOnly))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");

        MockEnvironment prodWithDev = new MockEnvironment().withProperty("thi360.vnpay.mode", "MOCK");
        prodWithDev.setActiveProfiles("dev", "prod");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(prodWithDev))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThat(conditionMatches(prodWithDev)).isFalse();

        MockEnvironment prodWithTest = new MockEnvironment().withProperty("thi360.vnpay.mode", "MOCK");
        prodWithTest.setActiveProfiles("api", "worker", "test", "prod");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(prodWithTest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThat(conditionMatches(prodWithTest)).isFalse();
    }

    @Test
    void allowsMockOutsideDevTestE2eOnlyWhenRealKeysAreUnsetAndNeverPrintsSecret() {
        MockEnvironment demoNoKeys = new MockEnvironment().withProperty("thi360.vnpay.mode", "MOCK");
        demoNoKeys.setActiveProfiles("api");
        assertThat(VnpayModeStartupGuard.validate(demoNoKeys)).isEqualTo(VnpayMode.MOCK);
        // Không có dev/test/e2e -> khởi động được ở chế độ MOCK nhưng trang /mock-vnpay vẫn tắt.
        assertThat(conditionMatches(demoNoKeys)).isFalse();

        MockEnvironment withTmn = new MockEnvironment()
                .withProperty("thi360.vnpay.mode", "MOCK")
                .withProperty("thi360.vnpay.tmn-code", "SECRET_TMN_VALUE");
        withTmn.setActiveProfiles("api");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(withTmn))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("SECRET_TMN_VALUE");

        MockEnvironment withSecret = new MockEnvironment()
                .withProperty("thi360.vnpay.mode", "MOCK")
                .withProperty("THI360_VNPAY_HASH_SECRET", "MY_SUPER_SECRET_HASH_KEY");
        withSecret.setActiveProfiles("api");
        assertThatThrownBy(() -> VnpayModeStartupGuard.validate(withSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("MY_SUPER_SECRET_HASH_KEY");
    }

    @Test
    void allowsSandboxAndProductionAndDisablesMockPageInSandbox() {
        MockEnvironment sandboxInDev = new MockEnvironment().withProperty("thi360.vnpay.mode", "SANDBOX");
        sandboxInDev.setActiveProfiles("dev");
        assertThat(VnpayModeStartupGuard.validate(sandboxInDev)).isEqualTo(VnpayMode.SANDBOX);
        assertThat(conditionMatches(sandboxInDev)).isFalse();

        MockEnvironment production = new MockEnvironment().withProperty("thi360.vnpay.mode", "PRODUCTION");
        production.setActiveProfiles("prod");
        assertThat(VnpayModeStartupGuard.validate(production)).isEqualTo(VnpayMode.PRODUCTION);
        assertThat(conditionMatches(production)).isFalse();
    }

    @Test
    void realApplicationYamlRejectsProdOrApiWithoutModeAndProdWithMock() {
        yamlRunner("api,prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("thi360.vnpay.mode");
        });

        yamlRunner("api").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("thi360.vnpay.mode");
        });

        yamlRunner("api,prod", "thi360.vnpay.mode=MOCK").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("prod");
        });
    }

    @Test
    void realApplicationYamlWiresMockInDevAndE2eAndTrimmedWhitespaceMockAndProductionInTestProd() {
        for (String profile : new String[] {"api,dev", "api,e2e"}) {
            yamlRunner(profile).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(VnpayModeStartupGuard.validate(context.getEnvironment())).isEqualTo(VnpayMode.MOCK);
                assertThat(context).hasSingleBean(VnpayGateway.class);
                assertThat(context.getBean(VnpayGateway.class)).isInstanceOf(MockVnpayGateway.class);
                assertThat(context).hasBean("mockVnpayPageConditionMarker");
            });
        }

        yamlRunner("api,dev", "thi360.vnpay.mode= MOCK ").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(VnpayModeStartupGuard.validate(context.getEnvironment())).isEqualTo(VnpayMode.MOCK);
            assertThat(context).hasSingleBean(VnpayGateway.class);
            assertThat(context.getBean(VnpayGateway.class)).isInstanceOf(MockVnpayGateway.class);
            assertThat(context).hasBean("mockVnpayPageConditionMarker");
        });

        yamlRunner("api,worker,test,prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(VnpayModeStartupGuard.validate(context.getEnvironment())).isEqualTo(VnpayMode.PRODUCTION);
            assertThat(context).hasSingleBean(VnpayGateway.class);
            assertThat(context.getBean(VnpayGateway.class)).isInstanceOf(UnsupportedVnpayGateway.class);
            assertThat(context.getBean(VnpayGateway.class)).isNotInstanceOf(MockVnpayGateway.class);
            assertThat(context).doesNotHaveBean("mockVnpayPageConditionMarker");
        });
    }

    private static ApplicationContextRunner yamlRunner(String activeProfiles, String... extraProperties) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + activeProfiles)
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(
                        VnpayConfiguration.class,
                        MockVnpayGateway.class,
                        MockPageMarkerConfig.class);
        if (extraProperties != null && extraProperties.length > 0) {
            runner = runner.withPropertyValues(extraProperties);
        }
        return runner;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VnpayProperties.class)
    static class MockPageMarkerConfig {
        @Bean
        @Conditional(MockVnpayPageCondition.class)
        String mockVnpayPageConditionMarker() {
            return "enabled";
        }
    }

    private static boolean conditionMatches(MockEnvironment env) {
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return new MockVnpayPageCondition().matches(ctx, mock(AnnotatedTypeMetadata.class));
    }
}
