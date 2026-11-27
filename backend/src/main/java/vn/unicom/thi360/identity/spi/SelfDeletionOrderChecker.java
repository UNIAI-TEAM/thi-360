package vn.unicom.thi360.identity.spi;

import java.util.UUID;

public interface SelfDeletionOrderChecker {

    boolean hasPaidUnfulfilledOrder(UUID userId);

    boolean hasPendingPaymentOrder(UUID userId);
}
