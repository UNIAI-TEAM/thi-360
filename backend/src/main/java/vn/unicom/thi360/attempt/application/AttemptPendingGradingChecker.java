package vn.unicom.thi360.attempt.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.identity.spi.PendingGradingChecker;

@Component
public class AttemptPendingGradingChecker implements PendingGradingChecker {

    private static final List<AttemptState> PENDING_GRADING_STATES = List.of(
            AttemptState.GRADING,
            AttemptState.PENDING_REVIEW);

    private final AttemptRepository attemptRepository;

    public AttemptPendingGradingChecker(AttemptRepository attemptRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingGrading(UUID userId) {
        if (userId == null) {
            return false;
        }
        return attemptRepository.existsByUserIdAndStateIn(userId, PENDING_GRADING_STATES);
    }
}
