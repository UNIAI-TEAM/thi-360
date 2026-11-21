package vn.unicom.thi360.commerce.refund.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.refund.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> lockById(@Param("id") UUID id);

    @Query("SELECT r.orderId FROM Refund r WHERE r.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    List<Refund> findByOrderIdOrderByRequestedAtDesc(UUID orderId);

    List<Refund> findByOrderIdInOrderByRequestedAtDesc(Collection<UUID> orderIds);

    List<Refund> findByPaymentIdOrderByRequestedAtDesc(UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(r.amountVnd), 0)
            FROM Refund r
            WHERE r.paymentId = :paymentId
              AND r.state = vn.unicom.thi360.commerce.refund.domain.RefundState.SUCCEEDED
            """)
    long sumSucceededAmountByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(r.amountVnd), 0)
            FROM Refund r
            WHERE r.paymentId = :paymentId
              AND r.state IN (
                  vn.unicom.thi360.commerce.refund.domain.RefundState.PROCESSING,
                  vn.unicom.thi360.commerce.refund.domain.RefundState.UNKNOWN
              )
            """)
    long sumActiveAmountByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Refund r
            WHERE r.paymentId = :paymentId
              AND r.state IN (
                  vn.unicom.thi360.commerce.refund.domain.RefundState.PROCESSING,
                  vn.unicom.thi360.commerce.refund.domain.RefundState.UNKNOWN
              )
            """)
    boolean existsActiveByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Refund r
            WHERE r.paymentId = :paymentId
              AND r.kind = vn.unicom.thi360.commerce.refund.domain.RefundKind.API
              AND r.state = vn.unicom.thi360.commerce.refund.domain.RefundState.FAILED
            """)
    boolean existsFailedApiByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Refund r, Payment p
            WHERE r.paymentId = p.id
              AND r.orderId = :orderId
              AND p.orderId = :orderId
              AND p.duplicate = false
              AND r.state IN (
                  vn.unicom.thi360.commerce.refund.domain.RefundState.PROCESSING,
                  vn.unicom.thi360.commerce.refund.domain.RefundState.UNKNOWN
              )
            """)
    boolean existsNonRejectedRefundOnCountedPayment(@Param("orderId") UUID orderId);

    @Query("""
            SELECT COALESCE(SUM(p.amountVnd), 0)
            FROM Payment p
            WHERE p.orderId = :orderId
              AND p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.SUCCEEDED
              AND p.duplicate = false
            """)
    long sumCountedPaidAmountByOrderId(@Param("orderId") UUID orderId);

    @Query("""
            SELECT COALESCE(SUM(r.amountVnd), 0)
            FROM Refund r, Payment p
            WHERE r.paymentId = p.id
              AND r.orderId = :orderId
              AND p.orderId = :orderId
              AND p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.SUCCEEDED
              AND p.duplicate = false
              AND r.state = vn.unicom.thi360.commerce.refund.domain.RefundState.SUCCEEDED
            """)
    long sumSucceededRefundsOnCountedPaymentsByOrderId(@Param("orderId") UUID orderId);
}
