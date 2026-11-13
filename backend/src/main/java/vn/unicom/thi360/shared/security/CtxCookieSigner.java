package vn.unicom.thi360.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class CtxCookieSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secretBytes;
    private final CtxProperties ctxProperties;
    private final Clock clock;

    public CtxCookieSigner(CtxProperties ctxProperties, Clock clock) {
        this.ctxProperties = Objects.requireNonNull(ctxProperties, "ctxProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        String secret = Objects.requireNonNull(ctxProperties.secret(), "ctxProperties.secret must not be null");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || bytes.length < CtxProperties.MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("thi360.ctx.secret must be at least 32 UTF-8 bytes");
        }
        this.secretBytes = bytes;
    }

    public String sign(UUID userId, Realm realm, Access access, long version) {
        Instant expiresAt = Instant.now(clock).plus(ctxProperties.ttl());
        return sign(userId, realm, access, version, expiresAt);
    }

    public String sign(UUID userId, Realm realm, Access access, long version, Instant expiresAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        String payload = userId
                + "."
                + realm.name()
                + "."
                + access.name()
                + "."
                + version
                + "."
                + expiresAt.getEpochSecond();
        byte[] sig = hmacSha256(payload);
        return payload + "." + URL_ENCODER.encodeToString(sig);
    }

    public Optional<CtxClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == token.length() - 1) {
            return Optional.empty();
        }
        String payload = token.substring(0, lastDot);
        String signaturePart = token.substring(lastDot + 1);

        byte[] expectedSig = hmacSha256(payload);
        String expectedEncoded = URL_ENCODER.encodeToString(expectedSig);
        if (!MessageDigest.isEqual(
                expectedEncoded.getBytes(StandardCharsets.US_ASCII),
                signaturePart.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\.");
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(parts[0]);
            Realm realm = Realm.valueOf(parts[1]);
            Access access = Access.valueOf(parts[2]);
            long version = Long.parseLong(parts[3]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            if (Instant.now(clock).isAfter(expiresAt)) {
                return Optional.empty();
            }
            return Optional.of(new CtxClaims(userId, realm, access, version, expiresAt));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private byte[] hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for thi360_ctx", ex);
        }
    }

    public record CtxClaims(
            UUID userId,
            Realm realm,
            Access access,
            long version,
            Instant expiresAt
    ) {
    }
}
