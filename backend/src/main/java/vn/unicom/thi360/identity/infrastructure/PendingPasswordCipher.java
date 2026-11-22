package vn.unicom.thi360.identity.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * Mã hóa đối xứng {@code AES-256-GCM} cho mật khẩu của phiên tự đăng ký đang chờ xác minh OTP (UNI-2773):
 * <ul>
 *   <li>Khóa 256-bit dẫn xuất từ {@code SHA-256("identity.pending_registration:" + THI360_TOKEN_PEPPER)};</li>
 *   <li>IV ngẫu nhiên 96-bit cho từng lần ghi, kèm {@code registrationId} làm dữ liệu xác thực bổ sung (AAD);</li>
 *   <li>Không bao giờ lưu mật khẩu dạng rõ trong cơ sở dữ liệu hay nhật ký.</li>
 * </ul>
 */
@Component
public class PendingPasswordCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PendingPasswordCipher(TokenPepperProperties pepper) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(
                    ("identity.pending_registration:" + pepper.pepper()).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public String encrypt(UUID registrationId, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(registrationId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(IV_BYTES + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt pending registration password", ex);
        }
    }

    public String decrypt(UUID registrationId, String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_BYTES) {
                throw new IllegalStateException("Invalid pending registration ciphertext");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES));
            cipher.updateAAD(registrationId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt pending registration password", ex);
        }
    }
}
