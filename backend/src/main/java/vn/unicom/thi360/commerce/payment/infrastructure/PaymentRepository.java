package vn.unicom.thi360.commerce.payment.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTxnRef(String txnRef);

    @Query("""
            SELECT new vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository$PaymentLookup(
                p.id, p.orderId, p.amountVnd)
            FROM Payment p WHERE p.txnRef = :txnRef
            """)
    Optional<PaymentLookup> findLookupByTxnRef(@Param("txnRef") String txnRef);

    record PaymentLookup(UUID id, UUID orderId, long amountVnd) {
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.txnRef = :txnRef")
    Optional<Payment> lockByTxnRef(@Param("txnRef") String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId ORDER BY p.attemptSeq ASC")
    List<Payment> lockAllByOrderIdOrderByAttemptSeqAsc(@Param("orderId") UUID orderId);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Payment> findByOrderIdInOrderByOrderIdAscCreatedAtDesc(List<UUID> orderIds);

    @Query("SELECT COALESCE(MAX(p.attemptSeq), 0) FROM Payment p WHERE p.orderId = :orderId")
    int findMaxAttemptSeqByOrderId(@Param("orderId") UUID orderId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM Payment p
            WHERE p.orderId = :orderId AND p.state = 'SUCCEEDED' AND p.duplicate = false
            """)
    boolean existsCountedPayment(@Param("orderId") UUID orderId);
}
