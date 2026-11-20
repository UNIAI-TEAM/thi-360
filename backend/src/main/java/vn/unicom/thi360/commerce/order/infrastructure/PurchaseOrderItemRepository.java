package vn.unicom.thi360.commerce.order.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrderItem;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    List<PurchaseOrderItem> findByOrderIdOrderByPositionAsc(UUID orderId);

    List<PurchaseOrderItem> findByOrderIdInOrderByOrderIdAscPositionAsc(Collection<UUID> orderIds);
}
