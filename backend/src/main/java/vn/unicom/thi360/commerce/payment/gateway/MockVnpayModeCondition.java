package vn.unicom.thi360.commerce.payment.gateway;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Điều kiện chung kiểm tra {@code thi360.vnpay.mode} (hoặc {@code THI360_VNPAY_MODE}) có giá trị {@code MOCK}
 * sau khi cắt khoảng trắng và không phân biệt hoa/thường, khớp với {@link VnpayModeStartupGuard}.
 */
public class MockVnpayModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isMockMode(context.getEnvironment());
    }

    public static boolean isMockMode(Environment env) {
        if (env == null) {
            return false;
        }
        String mode = env.getProperty("thi360.vnpay.mode");
        if (!StringUtils.hasText(mode)) {
            mode = env.getProperty("THI360_VNPAY_MODE");
        }
        return StringUtils.hasText(mode) && "MOCK".equalsIgnoreCase(mode.trim());
    }
}
