package vn.unicom.thi360.commerce.admin.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.refund.domain.RefundState;

/**
 * Suy diễn {@link AdminPaymentStatus}, {@link AdminRefundStatus} và danh sách {@link OrderAttentionType}
 * thống nhất cho cả danh sách lẫn chi tiết đơn hàng (TM 8.1, 9.1, BS §14).
 */
public final class OrderStatusDeriver {

    public record PaymentSnapshot(
            PaymentState state,
            boolean duplicate,
            LateSuccessReview lateSuccessReview,
            long amountVnd,
            long succeededRefundedVnd,
            Instant createdAt
    ) {
    }

    public record RefundSnapshot(RefundState state, long amountVnd) {
    }

    private OrderStatusDeriver() {
    }

    public static AdminPaymentStatus derivePaymentStatus(
            PaymentMethod paymentMethod, Collection<PaymentSnapshot> payments) {
        if (payments == null || payments.isEmpty()) {
            return paymentMethod == PaymentMethod.FREE ? AdminPaymentStatus.FREE : AdminPaymentStatus.PENDING;
        }
        boolean hasSucceeded = false;
        boolean hasPending = false;
        for (PaymentSnapshot payment : payments) {
            if (payment.state() == PaymentState.SUCCEEDED) {
                hasSucceeded = true;
                break;
            }
            if (payment.state() == PaymentState.CREATED || payment.state() == PaymentState.UNKNOWN) {
                hasPending = true;
            }
        }
        if (hasSucceeded) {
            return AdminPaymentStatus.SUCCEEDED;
        }
        if (hasPending) {
            return AdminPaymentStatus.PENDING;
        }
        return AdminPaymentStatus.FAILED;
    }

    public static AdminRefundStatus deriveRefundStatus(
            Collection<PaymentSnapshot> payments, Collection<RefundSnapshot> refunds) {
        if (refunds != null) {
            for (RefundSnapshot refund : refunds) {
                if (refund.state() == RefundState.PROCESSING || refund.state() == RefundState.UNKNOWN) {
                    return AdminRefundStatus.IN_PROGRESS;
                }
            }
        }
        long totalCollected = 0L;
        if (payments != null) {
            for (PaymentSnapshot payment : payments) {
                if (payment.state() == PaymentState.SUCCEEDED) {
                    totalCollected += payment.amountVnd();
                }
            }
        }
        long totalSucceededRefunded = 0L;
        if (refunds != null) {
            for (RefundSnapshot refund : refunds) {
                if (refund.state() == RefundState.SUCCEEDED) {
                    totalSucceededRefunded += refund.amountVnd();
                }
            }
        }
        if (totalCollected > 0L && totalSucceededRefunded >= totalCollected) {
            return AdminRefundStatus.FULL;
        }
        if (totalSucceededRefunded > 0L) {
            return AdminRefundStatus.PARTIAL;
        }
        return AdminRefundStatus.NONE;
    }

    public static List<OrderAttentionType> deriveAttentionTypes(
            OrderState orderState,
            boolean needsReview,
            Collection<PaymentSnapshot> payments,
            Collection<RefundSnapshot> refunds,
            Instant unknownThreshold24h) {
        boolean lateSuccessPending = false;
        boolean unknownOver24h = false;
        boolean duplicateUnrefunded = false;
        if (payments != null) {
            for (PaymentSnapshot p : payments) {
                if (p.lateSuccessReview() == LateSuccessReview.PENDING) {
                    lateSuccessPending = true;
                }
                if (p.state() == PaymentState.UNKNOWN
                        && p.createdAt() != null
                        && p.createdAt().isBefore(unknownThreshold24h)) {
                    unknownOver24h = true;
                }
                if (p.state() == PaymentState.SUCCEEDED
                        && p.duplicate()
                        && p.amountVnd() > p.succeededRefundedVnd()) {
                    duplicateUnrefunded = true;
                }
            }
        }
        boolean refundUnknown = false;
        if (refunds != null) {
            for (RefundSnapshot r : refunds) {
                if (r.state() == RefundState.UNKNOWN) {
                    refundUnknown = true;
                    break;
                }
            }
        }

        List<OrderAttentionType> types = new ArrayList<>(6);
        if (lateSuccessPending) {
            types.add(OrderAttentionType.LATE_SUCCESS_PENDING);
        }
        if (orderState == OrderState.PAID) {
            types.add(OrderAttentionType.PAID_UNFULFILLED);
        }
        if (unknownOver24h) {
            types.add(OrderAttentionType.PAYMENT_UNKNOWN_OVER_24H);
        }
        if (duplicateUnrefunded) {
            types.add(OrderAttentionType.DUPLICATE_UNREFUNDED);
        }
        if (needsReview) {
            types.add(OrderAttentionType.NEEDS_REVIEW);
        }
        if (refundUnknown) {
            types.add(OrderAttentionType.REFUND_UNKNOWN);
        }
        return List.copyOf(types);
    }
}
