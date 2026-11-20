package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/**
 * Module-internal hook invoked when an attempt part closes immediately before a part that requires
 * after-start selection (HSA/QDA Part 3, plan B1).
 *
 * <p>AT-2a defaults to {@link #NOOP} (leaving {@code nextPart} {@code PENDING}); AT-2b provides the
 * implementation that transitions {@code nextPart} to {@code AWAITING_CHOICE}.
 */
@FunctionalInterface
public interface PartTransitionHook {

    PartTransitionHook NOOP = (attempt, closedPart, nextPart, closedAt) -> {};

    void onPartClosedBeforeChoice(
            Attempt attempt, AttemptPart closedPart, AttemptPart nextPart, Instant closedAt);
}
