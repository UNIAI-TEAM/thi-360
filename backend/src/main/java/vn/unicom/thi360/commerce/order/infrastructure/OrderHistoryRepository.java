package vn.unicom.thi360.commerce.order.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {

    List<OrderHistory> findByOrderIdOrderByAtAsc(UUID orderId);
}
