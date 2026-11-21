package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Thrown when an essay grading item is actively claimed by another grader within the 30-minute window (AC-1..AC-3). */
public class GradingClaimedException extends RuntimeException {

    private final UUID claimedById;
    private final String claimedByName;
    private final Instant claimedAt;
    private final Instant lastActivityAt;

    public GradingClaimedException(
            UUID claimedById,
            String claimedByName,
            Instant claimedAt,
            Instant lastActivityAt) {
        super("GRADING_CLAIMED");
        this.claimedById = Objects.requireNonNull(claimedById, "claimedById must not be null");
        this.claimedByName = claimedByName;
        this.claimedAt = claimedAt;
        this.lastActivityAt = lastActivityAt;
    }

    public UUID getClaimedById() {
        return claimedById;
    }

    public String getClaimedByName() {
        return claimedByName;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
