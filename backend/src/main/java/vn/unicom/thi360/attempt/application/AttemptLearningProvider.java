package vn.unicom.thi360.attempt.application;

import java.math.BigDecimal;
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
 * Contributes the {@code attempts} section to the student learning profile (plan B1, AC-32, LP).
 * Excludes {@code STARTING} and {@code ABORTED} attempts, returns {@link StudentLearningData#empty()}
 * when the user has none, and strictly suppresses {@code score} and {@code maxScore} unless {@code state == SCORED}.
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
                        userId, EXCLUDED_STATES)
                .stream()
                .filter(a -> a.getState() != AttemptState.STARTING && a.getState() != AttemptState.ABORTED)
                .toList();
        if (attempts.isEmpty()) {
            return StudentLearningData.empty();
        }
        List<AttemptItem> items = attempts.stream()
                .map(AttemptLearningProvider::toAttemptItem)
                .toList();
        return new StudentLearningData(true, List.of(), items, List.of(), List.of(), List.of());
    }

    private static AttemptItem toAttemptItem(Attempt attempt) {
        AttemptLearningStatus status = mapStatus(attempt.getState());
        boolean scored = attempt.getState() == AttemptState.SCORED;
        BigDecimal score = scored
                ? (attempt.getDisplayScore() != null ? attempt.getDisplayScore() : attempt.getRawScore())
                : null;
        BigDecimal maxScore = !scored
                ? null
                : (attempt.getDisplayScore() != null ? resolveScaleMax(attempt) : attempt.getMaxScore());
        return new AttemptItem(
                attempt.getId(),
                attempt.getExamId(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getAttemptNo(),
                status,
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                score,
                maxScore);
    }

    private static AttemptLearningStatus mapStatus(AttemptState state) {
        return switch (state) {
            case IN_PROGRESS -> AttemptLearningStatus.IN_PROGRESS;
            case GRADING -> AttemptLearningStatus.GRADING;
            case PENDING_REVIEW -> AttemptLearningStatus.PENDING_REVIEW;
            case SCORED -> AttemptLearningStatus.SCORED;
            case GRADING_ERROR -> AttemptLearningStatus.GRADING_ERROR;
            case STARTING, ABORTED -> throw new IllegalStateException("Excluded attempt state reached mapping: " + state);
        };
    }

    private static BigDecimal resolveScaleMax(Attempt attempt) {
        if (attempt.getResult() != null && attempt.getResult().get("scaleMax") != null) {
            Object raw = attempt.getResult().get("scaleMax");
            if (raw instanceof BigDecimal bd) {
                return bd;
            }
            if (raw instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            try {
                return new BigDecimal(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // Fall back to referenceMax / maxScore
            }
        }
        if ("LINEAR".equals(attempt.getPracticeScoring())
                && attempt.getReferenceMax() != null
                && attempt.getReferenceMax().compareTo(BigDecimal.ZERO) > 0) {
            return attempt.getReferenceMax();
        }
        return attempt.getMaxScore();
    }
}
