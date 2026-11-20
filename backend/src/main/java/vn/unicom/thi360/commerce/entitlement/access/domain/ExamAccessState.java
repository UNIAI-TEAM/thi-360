package vn.unicom.thi360.commerce.entitlement.access.domain;

/**
 * What an account can do with one product (TM 10.2). The declaration order is the precedence order: the state of a
 * product is the first one that holds.
 */
public enum ExamAccessState {
    /** An attempt is open, also while its paper is still being generated ({@code STARTING}). */
    IN_PROGRESS,
    /** No open attempt and at least one attempt left. */
    CAN_START,
    /** No usable attempt, but a paid order is not fulfilled yet. */
    CHECKING,
    /** An entitlement exists and every attempt of it is gone. */
    EXHAUSTED,
    /** No entitlement, or a code that is not a product. */
    NONE;

    /**
     * The state of one product for one account, by TM 10.2 precedence.
     *
     * @param hasEntitlement the account has a purchase entitlement for the exam
     * @param attemptsLeft attempts still usable on it (0 without entitlement)
     * @param openAttempt an attempt of the entitlement is open ({@code STARTING} counts)
     * @param pendingOrder a paid order of the account is not fulfilled yet
     */
    public static ExamAccessState of(boolean hasEntitlement, int attemptsLeft, boolean openAttempt,
            boolean pendingOrder) {
        if (openAttempt) {
            return IN_PROGRESS;
        }
        if (attemptsLeft > 0) {
            return CAN_START;
        }
        if (pendingOrder) {
            return CHECKING;
        }
        return hasEntitlement ? EXHAUSTED : NONE;
    }
}
