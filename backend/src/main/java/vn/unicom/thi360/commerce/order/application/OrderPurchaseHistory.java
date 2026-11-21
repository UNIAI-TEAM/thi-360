package vn.unicom.thi360.commerce.order.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.pricing.PurchaseHistory;

/**
 * Cài đặt thật {@link PurchaseHistory} thay thế {@code ProvisionalPurchaseHistory}: một tài khoản được coi
 * là từng mua thành công nếu có ít nhất một đơn ở trạng thái {@code FULFILLED} (TM E8).
 */
@Service
@Transactional(readOnly = true)
public class OrderPurchaseHistory implements PurchaseHistory {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public OrderPurchaseHistory(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
    }

    @Override
    public boolean hasFulfilledOrder(UUID userId) {
        if (userId == null) {
            return false;
        }
        return purchaseOrderRepository.existsFulfilledByUserId(userId);
    }
}
