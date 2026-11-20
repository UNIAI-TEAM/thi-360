package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-internal port invoked by {@link AttemptGuard} before reading or mutating an attempt so
 * overdue parts and attempts close lazily at their deadline (LT F11, plan B1).
 *
 * <p>Slice 1 provides {@link NoOpOverdueAttemptCloser}; AT-2a supplies the real implementation.
 */
public interface OverdueAttemptCloser {

    void closeIfOverdue(UUID attemptId, Instant now);
}
