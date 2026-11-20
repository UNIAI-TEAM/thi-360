package vn.unicom.thi360.commerce.entitlement.access.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * INTERIM implementation (plan B1): the order tables do not exist yet, so no order is ever paid-but-unfulfilled.
 * CM-3a deletes this class together with {@code NoPendingOrderLookupTest} and registers the real lookup.
 */
@Component
public class NoPendingOrderLookup implements PendingOrderLookup {

    @Override
    public List<PendingOrder> findPaidUnfulfilled(UUID userId) {
        return List.of();
    }
}
