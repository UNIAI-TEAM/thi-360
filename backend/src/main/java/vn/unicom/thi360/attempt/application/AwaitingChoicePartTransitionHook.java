package vn.unicom.thi360.attempt.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartState;

/**
 * Transitions an after-start choice part (HSA/QDA Part 3) from {@link PartState#PENDING} to
 * {@link PartState#AWAITING_CHOICE} with a 15-minute window anchored at the preceding part's
 * {@code closedAt} (LT §6.1, L4).
 */
@Component
public class AwaitingChoicePartTransitionHook implements PartTransitionHook {

    public static final Duration CHOICE_WINDOW = Duration.ofMinutes(15);

    @Override
    public void onPartClosedBeforeChoice(
            Attempt attempt, AttemptPart closedPart, AttemptPart nextPart, Instant closedAt) {
        Objects.requireNonNull(closedAt, "closedAt must not be null");
        if (nextPart != null && nextPart.getState() == PartState.PENDING) {
            nextPart.awaitChoice(closedAt.plus(CHOICE_WINDOW));
        }
    }
}
