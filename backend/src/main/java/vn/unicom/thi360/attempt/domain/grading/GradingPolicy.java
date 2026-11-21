package vn.unicom.thi360.attempt.domain.grading;

import java.util.Objects;

/**
 * Exam-kind grading policy governing item-level auto-grading nuances.
 */
public record GradingPolicy(MultipleChoicePartialMode multipleChoicePartialMode) {

    public enum MultipleChoicePartialMode {
        /**
         * Awards {@code pointsMax * correctChosen / totalCorrect} when zero wrong options are
         * selected, and {@code 0} when any wrong option is selected (BE §8).
         */
        PROPORTIONAL,

        /**
         * H-SCA policy (BS §17.5, D17 / Q-32): awards {@code pointsMax * 1/2} when at least one
         * correct option is selected, fewer than all correct options are selected, and zero wrong
         * options are selected; {@code 0} if any wrong option is selected; full credit if all
         * correct options are selected.
         */
        HALF_CREDIT
    }

    public GradingPolicy {
        Objects.requireNonNull(multipleChoicePartialMode, "multipleChoicePartialMode must not be null");
    }

    public static GradingPolicy standard() {
        return new GradingPolicy(MultipleChoicePartialMode.PROPORTIONAL);
    }

    public static GradingPolicy halfCredit() {
        return new GradingPolicy(MultipleChoicePartialMode.HALF_CREDIT);
    }
}
