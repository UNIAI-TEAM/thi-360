package vn.unicom.thi360.commerce.payment.vnpay;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Điều kiện kích hoạt cổng VNPAY thật khi {@code thi360.vnpay.mode} (hoặc {@code THI360_VNPAY_MODE})
 * là {@code SANDBOX} hoặc {@code PRODUCTION} (không phân biệt hoa/thường, cắt khoảng trắng).
 */
public class VnpayRealModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isRealMode(context.getEnvironment());
    }

    public static boolean isRealMode(Environment env) {
        if (env == null) {
            return false;
        }
        String mode = env.getProperty("thi360.vnpay.mode");
        if (!StringUtils.hasText(mode)) {
            mode = env.getProperty("THI360_VNPAY_MODE");
        }
        if (!StringUtils.hasText(mode)) {
            return false;
        }
        String normalized = mode.trim();
        return "SANDBOX".equalsIgnoreCase(normalized) || "PRODUCTION".equalsIgnoreCase(normalized);
    }
}
