package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayPageCondition;
import vn.unicom.thi360.commerce.payment.gateway.VnpayMode;
import vn.unicom.thi360.commerce.payment.gateway.VnpayModeStartupGuard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private static boolean conditionMatches(MockEnvironment env) {
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return new MockVnpayPageCondition().matches(ctx, mock(AnnotatedTypeMetadata.class));
    }
}
