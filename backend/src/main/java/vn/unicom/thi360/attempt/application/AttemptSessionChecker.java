package vn.unicom.thi360.attempt.application;

import java.util.UUID;
import vn.unicom.thi360.attempt.domain.Attempt;

/**
 * Module-internal port invoked by {@link AttemptGuard} on edit commands to verify the caller
 * holds the active edit session (LT F10, plan B1).
 *
 * <p>Slice 1 provides {@link NoOpAttemptSessionChecker}; AT-3 supplies the real implementation.
 */
public interface AttemptSessionChecker {

    void requireEditSession(Attempt attempt, UUID sessionId);
}
