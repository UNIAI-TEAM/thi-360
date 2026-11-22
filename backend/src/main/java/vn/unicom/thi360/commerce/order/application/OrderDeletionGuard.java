package vn.unicom.thi360.commerce.order.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.identity.spi.AccountDeletionGuard;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Chặn xóa tài khoản khi người dùng đã phát sinh đơn hàng (TM 14, plan B1, AC-40).
 */
@Component
public class OrderDeletionGuard implements AccountDeletionGuard {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AppMessages messages;

    public OrderDeletionGuard(PurchaseOrderRepository purchaseOrderRepository, AppMessages messages) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.messages = Objects.requireNonNull(messages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Blocker> check(UUID userId) {
        if (userId == null || !purchaseOrderRepository.existsByUserId(userId)) {
            return List.of();
        }
        return List.of(new Blocker("COMMERCE", "USER_HAS_ORDERS", messages.get("USER_HAS_ORDERS")));
    }
}
