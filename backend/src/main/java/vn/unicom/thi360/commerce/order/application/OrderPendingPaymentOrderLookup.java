package vn.unicom.thi360.commerce.order.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.pricing.PendingPaymentOrderLookup;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;

/**
 * Cài đặt thật {@link PendingPaymentOrderLookup}: tìm đơn {@code PENDING_PAYMENT} hiện tại của học viên cho
 * sản phẩm đang báo giá để không tự chặn lượt giữ chỗ của chính mình (TM 7.2 bước 7, AC-39).
 */
@Component
@Transactional(readOnly = true)
public class OrderPendingPaymentOrderLookup implements PendingPaymentOrderLookup {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public OrderPendingPaymentOrderLookup(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
    }

    @Override
    public Optional<UUID> findPendingPaymentOrderId(UUID userId, String productCodeNormalized) {
        if (userId == null || productCodeNormalized == null || productCodeNormalized.isBlank()) {
            return Optional.empty();
        }
        return purchaseOrderRepository.findPendingPaymentOrderId(
                userId, StorefrontSearchText.normalize(productCodeNormalized));
    }
}
