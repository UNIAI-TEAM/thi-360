package vn.unicom.thi360.attempt.application;

import vn.unicom.thi360.shared.api.RateLimitedException;

/** Thrown when per-attempt answer save rate limit is exceeded (LT §7.1, AC-9). */
public class AttemptRateLimitedException extends RateLimitedException {

    private final long retryAfterSeconds;

    public AttemptRateLimitedException(long retryAfterSeconds) {
        super("RATE_LIMITED");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
