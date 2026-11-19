package vn.unicom.thi360.identity.application;

import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Chốt chặn khởi động của {@code THI360_DEV_EMAIL_CODE} (TM 5.3): chỉ profile {@code dev} được đặt biến này, và
 * giá trị phải là 6 chữ số. Đọc thẳng biến môi trường, không qua khóa của khối {@code dev}, nên đặt nhầm ở
 * {@code test}, {@code e2e} hay môi trường thật cũng bị bắt. Thông báo lỗi không in giá trị.
 */
@Component
public class DevEmailCodeGuard {

    static final String ENV_NAME = "THI360_DEV_EMAIL_CODE";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    public DevEmailCodeGuard(Environment environment, DevEmailCodeProperties properties) {
        String raw = environment.getProperty(ENV_NAME);
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        if (raw != null && !raw.isBlank() && !dev) {
            throw new IllegalStateException(
                    ENV_NAME + " is set but the dev profile is not active; refusing to start");
        }
        String code = properties.devCode();
        if (dev && code != null && !code.isBlank() && !SIX_DIGITS.matcher(code.trim()).matches()) {
            throw new IllegalStateException(ENV_NAME + " must be exactly 6 digits");
        }
    }
}
