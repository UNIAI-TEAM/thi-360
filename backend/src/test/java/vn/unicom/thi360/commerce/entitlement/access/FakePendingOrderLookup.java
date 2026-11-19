package vn.unicom.thi360.commerce.entitlement.access;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup;

/** A hand-driven {@link PendingOrderLookup}: the test says which paid orders of which account are not fulfilled. */
final class FakePendingOrderLookup implements PendingOrderLookup {

    private record Entry(UUID userId, PendingOrder order) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    void add(UUID userId, String orderCode, UUID examId) {
        entries.add(new Entry(userId, new PendingOrder(orderCode, examId)));
    }

    void clear() {
        entries.clear();
    }

    @Override
    public List<PendingOrder> findPaidUnfulfilled(UUID userId) {
        List<PendingOrder> orders = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.userId().equals(userId)) {
                orders.add(entry.order());
            }
        }
        return orders;
    }
}
