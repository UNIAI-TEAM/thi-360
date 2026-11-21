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
        PROPORTIONAL
    }

    public GradingPolicy {
        Objects.requireNonNull(multipleChoicePartialMode, "multipleChoicePartialMode must not be null");
    }

    public static GradingPolicy standard() {
        return new GradingPolicy(MultipleChoicePartialMode.PROPORTIONAL);
    }
}
