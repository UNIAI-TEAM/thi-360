package vn.unicom.thi360.attempt.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.identity.spi.ActiveAttemptChecker;

/** Checks whether a user currently has an open ({@code STARTING} or {@code IN_PROGRESS}) attempt (AC-32). */
@Component
public class AttemptActiveChecker implements ActiveAttemptChecker {

    private static final List<AttemptState> OPEN_STATES = List.of(
            AttemptState.STARTING,
            AttemptState.IN_PROGRESS);

    private final AttemptRepository attemptRepository;

    public AttemptActiveChecker(AttemptRepository attemptRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveAttempt(UUID userId) {
        if (userId == null) {
            return false;
        }
        return attemptRepository.existsByUserIdAndStateIn(userId, OPEN_STATES);
    }
}
