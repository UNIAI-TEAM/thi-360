package vn.unicom.thi360.commerce.pricing;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Temporary implementation of {@link PurchaseHistory} for CM-2 (plan B1): always treats every account as having no
 * {@code FULFILLED} order yet. CM-3a replaces this bean with the real query over {@code commerce.purchase_order}.
 */
@Component
public class ProvisionalPurchaseHistory implements PurchaseHistory {

    @Override
    public boolean hasFulfilledOrder(UUID userId) {
        return false;
    }
}
