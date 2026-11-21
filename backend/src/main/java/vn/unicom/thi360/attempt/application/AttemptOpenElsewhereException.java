package vn.unicom.thi360.attempt.application;

import java.time.Instant;

/**
 * Thrown when claiming an attempt edit session already held by another device without takeover
 * (LT F10, AC-13).
 */
public class AttemptOpenElsewhereException extends RuntimeException {

    private final Instant lastSeenAt;

    public AttemptOpenElsewhereException(Instant lastSeenAt) {
        super("Attempt is open in another session");
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
