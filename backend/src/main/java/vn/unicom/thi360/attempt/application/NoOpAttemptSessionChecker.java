package vn.unicom.thi360.attempt.application;

import java.util.UUID;
import vn.unicom.thi360.attempt.domain.Attempt;

/** Temporary no-op {@link AttemptSessionChecker} for AT-1 until AT-3 implements edit sessions (plan B1). */
public class NoOpAttemptSessionChecker implements AttemptSessionChecker {

    @Override
    public void requireEditSession(Attempt attempt, UUID sessionId) {
        // Intentional no-op until AT-3 delivers edit session enforcement.
    }
}
