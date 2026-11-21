package vn.unicom.thi360.commerce.admin.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.payment.domain.Payment;

/**
 * Cập nhật trạng thái ca tiền về muộn (BS §14 Ca C) trực tiếp trên {@link Payment} mà không sửa
 * {@code Payment.java} hay {@code PaymentOutcomeApplier.java}. Luôn detach entity {@code Payment}
 * đã khóa trước khi gọi các hàm {@code @Modifying} này.
 */
public interface AdminLateSuccessPaymentRepository extends Repository<Payment, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            UPDATE Payment p
            SET p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.SUCCEEDED,
                p.duplicate = false,
                p.excessReason = null,
                p.lateSuccessReview = vn.unicom.thi360.commerce.payment.domain.LateSuccessReview.ACCEPTED,
                p.updatedAt = :now,
                p.version = p.version + 1
            WHERE p.id = :paymentId
              AND p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.FAILED
              AND p.lateSuccessReview = vn.unicom.thi360.commerce.payment.domain.LateSuccessReview.PENDING
            """)
    int acceptPendingLateSuccess(@Param("paymentId") UUID paymentId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            UPDATE Payment p
            SET p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.SUCCEEDED,
                p.duplicate = true,
                p.excessReason = vn.unicom.thi360.commerce.payment.domain.ExcessReason.REJECTED_AFTER_RECONCILE,
                p.lateSuccessReview = vn.unicom.thi360.commerce.payment.domain.LateSuccessReview.REJECTED,
                p.updatedAt = :now,
                p.version = p.version + 1
            WHERE p.id = :paymentId
              AND p.state = vn.unicom.thi360.commerce.payment.domain.PaymentState.FAILED
              AND p.lateSuccessReview = vn.unicom.thi360.commerce.payment.domain.LateSuccessReview.PENDING
            """)
    int rejectPendingLateSuccessToExcess(@Param("paymentId") UUID paymentId, @Param("now") Instant now);
}
