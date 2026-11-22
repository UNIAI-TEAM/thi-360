package vn.unicom.thi360.commerce.order.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;

/**
 * Cài đặt thật {@link PendingOrderLookup} thay thế {@code NoPendingOrderLookup}: trả về danh sách bài thi
 * thuộc các đơn đã thu tiền ({@code PAID}) nhưng chưa cấp xong quyền (TM 10.2, 12, AC-39).
 */
@Service
@Transactional(readOnly = true)
public class OrderPendingOrderLookup implements PendingOrderLookup {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public OrderPendingOrderLookup(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
    }

    @Override
    public List<PendingOrder> findPaidUnfulfilled(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return purchaseOrderRepository.findPaidUnfulfilledExamRows(userId).stream()
                .map(row -> new PendingOrder(
                        (String) row[0],
                        row[1] instanceof UUID uuid ? uuid : UUID.fromString(row[1].toString())))
                .toList();
    }
}
