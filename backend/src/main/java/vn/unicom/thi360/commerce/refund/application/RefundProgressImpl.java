package vn.unicom.thi360.commerce.refund.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.fulfillment.application.RefundProgress;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundRepository;

/**
 * Cài đặt thật {@link RefundProgress} cho bước kiểm tra trước khi cấp quyền (TM 8.5 bước 1, BS 14).
 * Chỉ xét các lần hoàn gắn với khoản thanh toán được tính ({@code is_duplicate = false});
 * hoàn khoản trùng không bao giờ chặn hoặc hủy cấp quyền.
 */
@Component
public class RefundProgressImpl implements RefundProgress {

    private final RefundRepository refundRepository;

    public RefundProgressImpl(RefundRepository refundRepository) {
        this.refundRepository = Objects.requireNonNull(refundRepository, "refundRepository must not be null");
    }

    @Override
    public boolean hasNonRejectedRefund(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return refundRepository.existsNonRejectedRefundOnCountedPayment(orderId);
    }

    @Override
    public boolean isFullyRefunded(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        long countedPaid = refundRepository.sumCountedPaidAmountByOrderId(orderId);
        if (countedPaid <= 0L) {
            return false;
        }
        long succeededRefunded = refundRepository.sumSucceededRefundsOnCountedPaymentsByOrderId(orderId);
        return succeededRefunded >= countedPaid;
    }
}
