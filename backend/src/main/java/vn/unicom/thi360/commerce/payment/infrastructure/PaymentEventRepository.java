package vn.unicom.thi360.commerce.payment.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    List<PaymentEvent> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);
}
