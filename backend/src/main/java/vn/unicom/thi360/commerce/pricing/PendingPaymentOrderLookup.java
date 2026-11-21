package vn.unicom.thi360.commerce.pricing;

import java.util.Optional;
import java.util.UUID;

/**
 * Looks up a learner's active {@code PENDING_PAYMENT} order for a product so price quotes exclude that order's own
 * reserved promotion/voucher slots from {@code total_limit} and {@code per_user_limit} (TM 7.2 step 7, AC-39).
 */
@FunctionalInterface
public interface PendingPaymentOrderLookup {

    Optional<UUID> findPendingPaymentOrderId(UUID userId, String productCodeNormalized);
}
