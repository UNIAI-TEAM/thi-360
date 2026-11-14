package vn.unicom.thi360.shared.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class EncryptedObjectStorage {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final byte[] MAGIC = {'T', '3', '6', '0'};
    private static final byte VERSION_1 = 1;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int HEADER_BYTES = MAGIC.length + 1 + IV_LENGTH_BYTES;

    private final ObjectStorage objectStorage;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedObjectStorage(ObjectStorage objectStorage, FileEncryptionProperties properties) {
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        if (!properties.isEncKeyValid()) {
            throw new IllegalArgumentException(
                    "thi360.file.enc-key must be a Base64-encoded 32-byte AES key");
        }
        byte[] rawKey = Base64.getDecoder().decode(properties.encKey().trim());
        this.secretKey = new SecretKeySpec(rawKey, "AES");
    }

    public void putEncrypted(String key, byte[] plaintext) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] encrypted = encryptBytes(plaintext);
        objectStorage.put(key, encrypted, "application/octet-stream");
    }

    public Optional<byte[]> getDecrypted(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return objectStorage.get(key).map(this::decryptBytes);
    }

    public void delete(String key) {
        objectStorage.delete(key);
    }

    public boolean exists(String key) {
        return objectStorage.exists(key);
    }

    public String encryptText(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptText(String encodedCiphertext) {
        if (encodedCiphertext == null) {
            return null;
        }
        byte[] envelope = Base64.getDecoder().decode(encodedCiphertext);
        return new String(decryptBytes(envelope), StandardCharsets.UTF_8);
    }

    private byte[] encryptBytes(byte[] plaintext) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer out = ByteBuffer.allocate(HEADER_BYTES + ciphertext.length);
            out.put(MAGIC);
            out.put(VERSION_1);
            out.put(iv);
            out.put(ciphertext);
            return out.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt object payload", ex);
        }
    }

    private byte[] decryptBytes(byte[] envelope) {
        if (envelope.length <= HEADER_BYTES) {
            throw new IllegalStateException("Encrypted object payload is truncated");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (envelope[i] != MAGIC[i]) {
                throw new IllegalStateException("Encrypted object payload has invalid header");
            }
        }
        if (envelope[MAGIC.length] != VERSION_1) {
            throw new IllegalStateException("Unsupported encrypted object version: " + envelope[MAGIC.length]);
        }
        byte[] iv = Arrays.copyOfRange(envelope, MAGIC.length + 1, HEADER_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, HEADER_BYTES, envelope.length);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt object payload", ex);
        }
    }
}
