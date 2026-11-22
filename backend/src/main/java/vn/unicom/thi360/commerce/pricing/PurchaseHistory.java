package vn.unicom.thi360.commerce.pricing;

import java.util.UUID;

/**
 * Checks whether an account qualifies as a "new account" for vouchers with {@code audience = NEW_ACCOUNT} (TM E8).
 */
public interface PurchaseHistory {

    boolean hasFulfilledOrder(UUID userId);
}
