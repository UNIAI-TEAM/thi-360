package vn.unicom.thi360.shared.storage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "thi360.file")
public record FileEncryptionProperties(
        @NotBlank(message = "thi360.file.enc-key is required (set THI360_FILE_ENC_KEY)")
        String encKey
) {

    public static final int REQUIRED_KEY_BYTES = 32;

    @AssertTrue(message = "thi360.file.enc-key must be a Base64-encoded 32-byte (256-bit) AES key")
    public boolean isEncKeyValid() {
        if (encKey == null || encKey.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encKey.trim());
            return decoded.length == REQUIRED_KEY_BYTES;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
