package vn.unicom.thi360.commerce.entitlement.access;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup;

/** A hand-driven {@link PendingOrderLookup}: the test says which paid orders of which account are not fulfilled. */
final class FakePendingOrderLookup implements PendingOrderLookup {

    private record Entry(UUID userId, PendingOrder order) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final List<String> observed = new CopyOnWriteArrayList<>();
    private volatile Supplier<String> probe;

    void add(UUID userId, String orderCode, UUID examId) {
        entries.add(new Entry(userId, new PendingOrder(orderCode, examId)));
    }

    /** From now on every lookup records what {@code probe} says at that moment (it runs inside the caller's transaction). */
    void observeWith(Supplier<String> probe) {
        this.probe = probe;
    }

    List<String> observed() {
        return List.copyOf(observed);
    }

    void clear() {
        entries.clear();
        observed.clear();
        probe = null;
    }

    @Override
    public List<PendingOrder> findPaidUnfulfilled(UUID userId) {
        Supplier<String> current = probe;
        if (current != null) {
            observed.add(current.get());
        }
        List<PendingOrder> orders = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.userId().equals(userId)) {
                orders.add(entry.order());
            }
        }
        return orders;
    }
}
