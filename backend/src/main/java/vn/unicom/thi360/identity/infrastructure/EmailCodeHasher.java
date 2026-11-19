package vn.unicom.thi360.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * HMAC-SHA256 của mã OTP, khóa là {@code THI360_TOKEN_PEPPER} (TM 5.3). Mã 6 số băm không khóa thì dò ngược
 * được nếu lộ bảng. Tài khoản và mục đích nằm trong thông điệp để cùng một mã ở hai tài khoản cho hai giá trị khác nhau.
 */
@Component
public class EmailCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public EmailCodeHasher(TokenPepperProperties pepper) {
        this.key = new SecretKeySpec(pepper.pepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(UUID userId, EmailCodePurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((userId + ":" + purpose.name() + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    public boolean matches(UUID userId, EmailCodePurpose purpose, String code, String storedHash) {
        if (code == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(userId, purpose, code).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
