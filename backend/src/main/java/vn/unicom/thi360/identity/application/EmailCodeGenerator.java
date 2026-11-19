package vn.unicom.thi360.identity.application;

import java.security.SecureRandom;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Sinh mã OTP 6 số (E15). Profile {@code dev} (không kèm profile khác ngoài api, worker) có {@code THI360_DEV_EMAIL_CODE} thì dùng mã cố định đó. */
@Component
public class EmailCodeGenerator {

    private final SecureRandom random = new SecureRandom();
    private final String fixedCode;

    public EmailCodeGenerator(Environment environment, DevEmailCodeProperties properties) {
        String code = properties.devCode();
        this.fixedCode = DevEmailCodeGuard.fixedCodeAllowed(environment) && code != null && !code.isBlank()
                ? code.trim()
                : null;
    }

    public String next() {
        return fixedCode != null ? fixedCode : String.format("%06d", random.nextInt(1_000_000));
    }
}
