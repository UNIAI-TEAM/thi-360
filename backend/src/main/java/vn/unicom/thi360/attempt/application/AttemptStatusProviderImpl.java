package vn.unicom.thi360.attempt.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;

/** Real AttemptStatusProvider implementation backed by attempt.attempt (AD-121 #3). */
@Component("attemptStatusProviderImpl")
@Transactional(readOnly = true)
public class AttemptStatusProviderImpl implements AttemptStatusProvider {

    private static final List<AttemptState> OPEN_STATES =
            List.of(AttemptState.STARTING, AttemptState.IN_PROGRESS);

    private final AttemptRepository attemptRepository;

    public AttemptStatusProviderImpl(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    public Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId) {
        if (userId == null || entitlementId == null) {
            return Optional.empty();
        }
        return attemptRepository
                .findFirstByUserIdAndEntitlementIdAndStateInOrderByCreatedAtDesc(
                        userId, entitlementId, OPEN_STATES)
                .map(attempt -> new OpenAttempt(attempt.getId(), toOpenAttemptState(attempt)));
    }

    @Override
    public Optional<ResultAttempt> findLatestResultAttempt(UUID userId, UUID entitlementId) {
        if (userId == null || entitlementId == null) {
            return Optional.empty();
        }
        return attemptRepository
                .findLatestResultAttempt(userId, entitlementId)
                .map(attempt -> new ResultAttempt(attempt.getId(), attempt.getFinishedAt()));
    }

    private static OpenAttemptState toOpenAttemptState(Attempt attempt) {
        return switch (attempt.getState()) {
            case STARTING -> OpenAttemptState.STARTING;
            case IN_PROGRESS -> OpenAttemptState.IN_PROGRESS;
            default -> throw new IllegalStateException("Unexpected open attempt state: " + attempt.getState());
        };
    }
}
