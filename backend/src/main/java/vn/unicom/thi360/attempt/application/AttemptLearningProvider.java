package vn.unicom.thi360.attempt.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;

/**
 * Contributes the {@code attempts} section to the student learning profile (plan B1, AC-32).
 * Excludes {@code STARTING} and {@code ABORTED} attempts and returns {@link StudentLearningData#empty()}
 * when the user has none so {@code dataAvailable} remains {@code false}.
 */
@Component("attemptStudentLearningProvider")
public class AttemptLearningProvider implements StudentLearningProvider {

    private static final List<AttemptState> EXCLUDED_STATES = List.of(
            AttemptState.STARTING,
            AttemptState.ABORTED);

    private final AttemptRepository attemptRepository;

    public AttemptLearningProvider(AttemptRepository attemptRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningData loadForUser(UUID userId) {
        if (userId == null) {
            return StudentLearningData.empty();
        }
        List<Attempt> attempts = attemptRepository.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, EXCLUDED_STATES);
        if (attempts.isEmpty()) {
            return StudentLearningData.empty();
        }
        List<Object> rows = attempts.stream()
                .<Object>map(a -> new AttemptLearningRow(
                        a.getId(),
                        a.getExamId(),
                        a.getExamName(),
                        a.getExamKindCode(),
                        a.getScope(),
                        a.getAttemptNo(),
                        a.getState(),
                        a.getStartedAt(),
                        a.getFinishedAt(),
                        a.getRawScore(),
                        a.getDisplayScore(),
                        a.getMaxScore()))
                .toList();
        return new StudentLearningData(true, List.of(), rows, List.of(), List.of(), List.of());
    }
}
