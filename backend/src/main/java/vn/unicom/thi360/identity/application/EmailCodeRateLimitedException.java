package vn.unicom.thi360.identity.application;

/**
 * Gửi mã OTP quá nhanh hoặc quá nhiều (E15, E17). {@code EmailCodeExceptionHandler} trả 429 {@code RATE_LIMITED}
 * kèm header {@code Retry-After} và trường {@code retryAfter} (giây).
 */
public class EmailCodeRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public EmailCodeRateLimitedException(long retryAfterSeconds) {
        super("Email code send limit reached");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
