package vn.unicom.thi360.attempt.domain.grading;

/**
 * Pure stateless H-SCA half-credit multiple-choice partial rule (BS §17.5, ADD-HV D17 / Q-32).
 *
 * <ul>
 *   <li>Any wrong option selected ({@code wrongChosen > 0}) or zero correct selected → {@code 0}</li>
 *   <li>All correct options selected with zero wrong options → {@code 1} (full credit)</li>
 *   <li>At least one correct option selected, strictly fewer than all, with zero wrong options →
 *       {@code 1/2} (half credit, regardless of whether 1 of 3, 2 of 3, 1 of 4, 2 of 4, or 3 of 4)</li>
 * </ul>
 */
public final class HalfCreditMultipleChoice {

    public static final ExactRational HALF = ExactRational.of(1, 2);

    private HalfCreditMultipleChoice() {}

    /**
     * Returns the exact score fraction in {@code {0, 1/2, 1}} for a non-blank multiple-choice answer
     * under H-SCA {@code PARTIAL} policy.
     */
    public static ExactRational partialFraction(int correctChosen, int totalCorrect, int wrongChosen) {
        if (wrongChosen > 0 || totalCorrect <= 0 || correctChosen <= 0) {
            return ExactRational.ZERO;
        }
        if (correctChosen >= totalCorrect) {
            return ExactRational.ONE;
        }
        return HALF;
    }
}
