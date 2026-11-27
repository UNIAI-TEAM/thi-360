package vn.unicom.thi360.commerce.order.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.identity.spi.SelfDeletionOrderChecker;

@Component
public class OrderSelfDeletionChecker implements SelfDeletionOrderChecker {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public OrderSelfDeletionChecker(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPaidUnfulfilledOrder(UUID userId) {
        return userId != null && purchaseOrderRepository.existsByUserIdAndState(userId, OrderState.PAID);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingPaymentOrder(UUID userId) {
        return userId != null && purchaseOrderRepository.existsByUserIdAndState(userId, OrderState.PENDING_PAYMENT);
    }
}
