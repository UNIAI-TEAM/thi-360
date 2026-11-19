package vn.unicom.thi360.commerce.entitlement.access.domain;

import java.util.ArrayList;
import java.util.List;

/** The buttons of a "Bài thi của tôi" card (TM 12). The list order is the order the card shows them, primary first. */
public enum MyExamAction {
    /** Go back into the open attempt. */
    CONTINUE,
    /** Open the prepare step for a new attempt. */
    START,
    /** Open the result of the latest attempt that has one. */
    VIEW_RESULT,
    /** Open the attempt history of the exam. */
    HISTORY,
    /** Buy again (TM 12): every attempt is gone ({@code EXHAUSTED}) and the product is still on sale. */
    REBUY,
    /** Buy more attempts (C13): attempts are left and the product is still on sale. */
    BUY_MORE,
    /** A paid order is being fulfilled: shown instead of any purchase button, not clickable. */
    CHECKING;

    /**
     * The actions of one card.
     *
     * @param state the access state of the card ({@code NONE} never has a card)
     * @param attemptsLeft attempts usable now
     * @param openAttempt an attempt is open ({@code STARTING} counts)
     * @param hasResult some attempt of the entitlement has a result
     * @param hasHistory the entitlement has had an attempt (used, held, open or with a result); an exhausted card has
     *        History whatever its attempts were (all of them may have been revoked unused)
     * @param onSale the product is still on sale
     */
    public static List<MyExamAction> of(ExamAccessState state, int attemptsLeft, boolean openAttempt,
            boolean hasResult, boolean hasHistory, boolean onSale) {
        List<MyExamAction> actions = new ArrayList<>();
        if (state == ExamAccessState.CHECKING) {
            actions.add(CHECKING);
        }
        if (openAttempt) {
            actions.add(CONTINUE);
        } else if (attemptsLeft > 0) {
            actions.add(START);
        }
        if (hasResult) {
            actions.add(VIEW_RESULT);
        }
        if (hasHistory || state == ExamAccessState.EXHAUSTED) {
            actions.add(HISTORY);
        }
        if (onSale) {
            if (state == ExamAccessState.EXHAUSTED) {
                actions.add(REBUY);
            } else if (attemptsLeft > 0 && state != ExamAccessState.CHECKING) {
                actions.add(BUY_MORE);
            }
        }
        return actions;
    }
}
