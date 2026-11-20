package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.util.UUID;

/** Temporary no-op {@link OverdueAttemptCloser} for AT-1 until AT-2a implements lazy closing (plan B1). */
public class NoOpOverdueAttemptCloser implements OverdueAttemptCloser {

    @Override
    public void closeIfOverdue(UUID attemptId, Instant now) {
        // Intentional no-op until AT-2a delivers the real overdue closer.
    }
}
