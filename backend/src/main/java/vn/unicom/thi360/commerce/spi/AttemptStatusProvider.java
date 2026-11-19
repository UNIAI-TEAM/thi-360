package vn.unicom.thi360.commerce.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port implemented by the attempt module and read inside commerce
 * (exam-access query, my-exams cards; TM 10.2, TM 12, LT 3). Commerce obtains
 * the implementation through an {@code ObjectProvider}; while no attempt module
 * is on the classpath the {@link #EMPTY} default reports no attempt at all.
 */
public interface AttemptStatusProvider {

    /**
     * Default implementation used when the attempt module provides no bean:
     * reports no open attempt and no finished attempt.
     */
    AttemptStatusProvider EMPTY = new AttemptStatusProvider() {
        @Override
        public Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId) {
            return Optional.empty();
        }

        @Override
        public Optional<ResultAttempt> findLatestResultAttempt(UUID userId, UUID entitlementId) {
            return Optional.empty();
        }
    };

    /**
     * The attempt currently open for this entitlement: {@code STARTING} counts
     * as open (TM 10.2: IN_PROGRESS covers paper generation as well).
     */
    Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId);

    /**
     * The most recent attempt of this entitlement that has a result, for the
     * VIEW_RESULT action of my-exams cards (TM 12).
     */
    Optional<ResultAttempt> findLatestResultAttempt(UUID userId, UUID entitlementId);

    enum OpenAttemptState {
        STARTING,
        IN_PROGRESS
    }

    record OpenAttempt(UUID attemptId, OpenAttemptState state) {
    }

    record ResultAttempt(UUID attemptId, Instant finishedAt) {
    }
}
