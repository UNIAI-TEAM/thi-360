package vn.unicom.thi360.commerce.payment.vnpay;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.payment.gateway.VnpayMode;

/**
 * Chốt chặn khởi động chế độ VNPAY thật ({@code SANDBOX} / {@code PRODUCTION}, TM 8.4, AC-21, AC-22):
 * kiểm tra đủ 4 biến cấu hình và tính hợp lệ của URL trước khi nhận traffic.
 * Không bao giờ in giá trị biến cấu hình hoặc khóa bí mật vào thông báo lỗi.
 */
public class VnpayRealModeStartupGuard implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(environment);
    }

    /** Kiểm tra chế độ và toàn bộ biến bắt buộc khi chạy {@code SANDBOX} hoặc {@code PRODUCTION}. */
    public static VnpayMode validate(Environment environment) {
        if (environment == null) {
            throw new IllegalStateException("Environment must not be null when validating THI360_VNPAY_MODE");
        }
        String rawMode = firstNonBlank(environment, "thi360.vnpay.mode", "THI360_VNPAY_MODE");
        if (!StringUtils.hasText(rawMode)) {
            throw new IllegalStateException(
                    "THI360_VNPAY_MODE (thi360.vnpay.mode) is required and must be MOCK, SANDBOX, or PRODUCTION");
        }
        VnpayMode mode;
        try {
            mode = VnpayMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid THI360_VNPAY_MODE (thi360.vnpay.mode); expected MOCK, SANDBOX, or PRODUCTION");
        }

        if (mode == VnpayMode.SANDBOX || mode == VnpayMode.PRODUCTION) {
            String tmnCode = firstNonBlank(environment, "thi360.vnpay.tmn-code", "THI360_VNPAY_TMN_CODE");
            String hashSecret = firstNonBlank(environment, "thi360.vnpay.hash-secret", "THI360_VNPAY_HASH_SECRET");
            String payUrl = firstNonBlank(environment, "thi360.vnpay.pay-url", "THI360_VNPAY_PAY_URL");
            String apiUrl = firstNonBlank(environment, "thi360.vnpay.api-url", "THI360_VNPAY_API_URL");

            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(tmnCode)) {
                missing.add("THI360_VNPAY_TMN_CODE");
            }
            if (!StringUtils.hasText(hashSecret)) {
                missing.add("THI360_VNPAY_HASH_SECRET");
            }
            if (!StringUtils.hasText(payUrl)) {
                missing.add("THI360_VNPAY_PAY_URL");
            }
            if (!StringUtils.hasText(apiUrl)) {
                missing.add("THI360_VNPAY_API_URL");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Missing required VNPAY configuration for mode " + mode + ": " + String.join(", ", missing));
            }

            validateHttpUri(payUrl, "THI360_VNPAY_PAY_URL");
            validateHttpUri(apiUrl, "THI360_VNPAY_API_URL");
        }

        return mode;
    }

    private static void validateHttpUri(String rawUrl, String envVarName) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(envVarName + " must be a valid http or https URI with a host");
        }
        String scheme = uri.getScheme();
        boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!validScheme || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(envVarName + " must be a valid http or https URI with a host");
        }
    }

    private static String firstNonBlank(Environment environment, String springProp, String envVar) {
        String primary = environment.getProperty(springProp);
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        String fallback = environment.getProperty(envVar);
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }
}
