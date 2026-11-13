package vn.unicom.thi360.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.RateLimitedException;

@Component
public class LoginThrottle {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    public LoginThrottle(StringRedisTemplate redisTemplate, SecurityProperties securityProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties must not be null");
    }

    public void assertNotBlocked(String normalizedIdentifier) {
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            return;
        }
        String hash = sha256(normalizedIdentifier);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + hash))) {
            throw new RateLimitedException("LOGIN_THROTTLED");
        }
    }

    public boolean recordFailure(String normalizedIdentifier) {
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            return false;
        }
        String hash = sha256(normalizedIdentifier);
        String failKey = FAIL_PREFIX + hash;
        String lockKey = LOCK_PREFIX + hash;

        Duration failureWindow = securityProperties.loginThrottle().failureWindow();
        Duration lockDuration = securityProperties.loginThrottle().lockDuration();
        int maxFailures = Math.max(1, securityProperties.loginThrottle().maxFailures());

        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, failureWindow);
        }
        if (count != null && count >= maxFailures) {
            redisTemplate.opsForValue().set(lockKey, "1", lockDuration);
            return true;
        }
        return false;
    }

    public void reset(String normalizedIdentifier) {
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            return;
        }
        String hash = sha256(normalizedIdentifier);
        redisTemplate.delete(java.util.List.of(FAIL_PREFIX + hash, LOCK_PREFIX + hash));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
