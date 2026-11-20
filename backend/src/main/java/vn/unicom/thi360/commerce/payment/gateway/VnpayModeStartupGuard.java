package vn.unicom.thi360.commerce.payment.gateway;

import java.util.Locale;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Chốt chặn khởi động chế độ VNPAY (TM 8.4, plan CM-3a AC-18):
 * <ul>
 *   <li>Thiếu hoặc giá trị {@code thi360.vnpay.mode} ({@code THI360_VNPAY_MODE}) không hợp lệ thì dừng khởi động.
 *   <li>{@code MOCK} cùng profile {@code prod} luôn bị từ chối.
 *   <li>{@code MOCK} ngoài {@code dev}, {@code test}, {@code e2e} chỉ được phép khi cả {@code THI360_VNPAY_TMN_CODE}
 *       và {@code THI360_VNPAY_HASH_SECRET} đều trống (bản demo chưa gắn khóa thật).
 * </ul>
 * Không bao giờ in giá trị bí mật vào thông báo lỗi.
 */
public class VnpayModeStartupGuard implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(environment);
    }

    /** Kiểm tra bảng quy tắc khởi động của {@code thi360.vnpay.mode}. */
    public static VnpayMode validate(Environment environment) {
        if (environment == null) {
            throw new IllegalStateException("Environment must not be null when validating thi360.vnpay.mode");
        }
        String rawMode = firstNonBlank(
                environment.getProperty("thi360.vnpay.mode"),
                environment.getProperty("THI360_VNPAY_MODE"));
        if (!StringUtils.hasText(rawMode)) {
            throw new IllegalStateException(
                    "thi360.vnpay.mode (THI360_VNPAY_MODE) is required and must be MOCK, SANDBOX, or PRODUCTION");
        }
        VnpayMode mode;
        try {
            mode = VnpayMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid thi360.vnpay.mode '" + rawMode.trim() + "'; expected MOCK, SANDBOX, or PRODUCTION", ex);
        }

        if (mode == VnpayMode.MOCK) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "thi360.vnpay.mode=MOCK is forbidden when profile 'prod' is active");
            }
            boolean devTestOrE2e = environment.acceptsProfiles(Profiles.of("dev | test | e2e"));
            if (!devTestOrE2e) {
                String tmnCode = firstNonBlank(
                        environment.getProperty("thi360.vnpay.tmn-code"),
                        environment.getProperty("THI360_VNPAY_TMN_CODE"));
                String hashSecret = firstNonBlank(
                        environment.getProperty("thi360.vnpay.hash-secret"),
                        environment.getProperty("THI360_VNPAY_HASH_SECRET"));
                if (StringUtils.hasText(tmnCode) || StringUtils.hasText(hashSecret)) {
                    throw new IllegalStateException(
                            "thi360.vnpay.mode=MOCK outside dev/test/e2e profiles is forbidden when "
                                    + "THI360_VNPAY_TMN_CODE or THI360_VNPAY_HASH_SECRET is configured");
                }
            }
        }
        return mode;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }
}
