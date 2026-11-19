package vn.unicom.thi360.identity.application;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Chốt chặn khởi động của {@code THI360_DEV_EMAIL_CODE} (TM 5.3): chỉ khi {@code dev} bật và mọi profile đang bật khác
 * đều là {@code api} hoặc {@code worker} (tổ hợp dev thông thường) thì mới được đặt biến này, và giá trị phải là 6 chữ
 * số. {@code dev} chung với {@code prod}, {@code e2e} hay {@code test} đều bị từ chối, bất kể thứ tự ưu tiên giữa các
 * profile, nên không có môi trường nào vừa là dev vừa là môi trường thật. Đọc thẳng biến môi trường, không qua khóa của
 * khối {@code dev}, nên đặt nhầm ở môi trường khác cũng bị bắt. Thông báo lỗi không in giá trị.
 */
@Component
public class DevEmailCodeGuard {

    static final String ENV_NAME = "THI360_DEV_EMAIL_CODE";
    private static final Set<String> DEV_COMPANION_PROFILES = Set.of("dev", "api", "worker");
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    public DevEmailCodeGuard(Environment environment, DevEmailCodeProperties properties) {
        String raw = environment.getProperty(ENV_NAME);
        boolean overrideSet = raw != null && !raw.isBlank();
        boolean allowed = fixedCodeAllowed(environment);
        if (overrideSet && !allowed) {
            List<String> others = incompatibleProfiles(environment);
            throw new IllegalStateException(
                    ENV_NAME + " is set but the profiles are not a dev-only combination (dev active: "
                            + environment.acceptsProfiles(Profiles.of("dev")) + ", other profiles: " + others
                            + "); refusing to start");
        }
        String code = properties.devCode();
        if (allowed && code != null && !code.isBlank() && !SIX_DIGITS.matcher(code.trim()).matches()) {
            throw new IllegalStateException(ENV_NAME + " must be exactly 6 digits");
        }
    }

    /** Mã cố định chỉ hợp lệ khi dev bật và không có profile nào ngoài {@code dev}, {@code api}, {@code worker}. */
    static boolean fixedCodeAllowed(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("dev")) && incompatibleProfiles(environment).isEmpty();
    }

    private static List<String> incompatibleProfiles(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> !DEV_COMPANION_PROFILES.contains(profile))
                .toList();
    }
}
