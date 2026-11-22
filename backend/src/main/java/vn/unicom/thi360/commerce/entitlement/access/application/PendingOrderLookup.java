package vn.unicom.thi360.commerce.entitlement.access.application;

import java.util.List;
import java.util.UUID;

/**
 * Orders of one account that are paid but not yet fulfilled (TM 10.2 {@code CHECKING}, TM 12 "Đang kiểm tra").
 * Temporary port of plan B1: CM-4b declares it with {@link NoPendingOrderLookup}, CM-3a replaces that class with a
 * reader of {@code purchase_order} (state {@code PAID}, not yet {@code FULFILLED}).
 */
public interface PendingOrderLookup {

    /**
     * Every paid, unfulfilled order of the account, oldest first, one entry per exam of the order (a bundle order
     * names several exams, CM-6). Empty when there is none.
     */
    List<PendingOrder> findPaidUnfulfilled(UUID userId);

    /**
     * @param orderCode public order code (E14)
     * @param examId exam the order is still to grant attempts for
     */
    record PendingOrder(String orderCode, UUID examId) {
    }
}
