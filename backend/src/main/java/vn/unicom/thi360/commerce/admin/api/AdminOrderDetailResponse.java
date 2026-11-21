package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.commerce.admin.domain.AdminPaymentStatus;
import vn.unicom.thi360.commerce.admin.domain.AdminRefundStatus;
import vn.unicom.thi360.commerce.admin.domain.OrderAttentionType;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.payment.domain.ExcessReason;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.refund.api.RefundResponse;

/** Chi tiết đơn hàng cho màn quản trị (TM 9.2, 9.4, 9.5, BS §14). */
public record AdminOrderDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BuyerInfo buyer,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
        UUID examId,
        Integer examVersionNo,
        UUID bundleId,
        Integer bundleVersionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> optionCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPriceVnd,
        UUID promotionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promotionDiscountVnd,
        UUID voucherId,
        String voucherCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voucherDiscountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountApplied discountApplied,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountDueVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> benefits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultView,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentMethod paymentMethod,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminPaymentStatus paymentStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminRefundStatus refundStatus,
        UUID replacedByOrderId,
        String replacedByOrderCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsReview,
        String reviewReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderAttentionType> attentionTypes,
        Instant expiresAt,
        Instant paidAt,
        Instant fulfilledAt,
        String fulfillmentError,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fulfillmentTries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PaymentDetailView> payments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GrantView> grants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<HistoryView> orderHistory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuditEventItem> auditEvents
) {
    public AdminOrderDetailResponse {
        optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
        benefits = benefits == null ? Map.of() : Map.copyOf(benefits);
        attentionTypes = attentionTypes == null ? List.of() : List.copyOf(attentionTypes);
        items = items == null ? List.of() : List.copyOf(items);
        payments = payments == null ? List.of() : List.copyOf(payments);
        grants = grants == null ? List.of() : List.copyOf(grants);
        orderHistory = orderHistory == null ? List.of() : List.copyOf(orderHistory);
        auditEvents = auditEvents == null ? List.of() : List.copyOf(auditEvents);
    }

    public record BuyerInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String username,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fullName
    ) {
    }

    public record OrderItemView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int examVersionNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> optionCodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> benefits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultView,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position
    ) {
        public OrderItemView {
            optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
            benefits = benefits == null ? Map.of() : Map.copyOf(benefits);
        }
    }

    public record PaymentDetailView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptSeq,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String txnRef,
            String vnpTransactionNo,
            String bankCode,
            String cardType,
            String payDate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentState state,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean duplicate,
            ExcessReason excessReason,
            LateSuccessReview lateSuccessReview,
            Instant lateSuccessAt,
            String responseCode,
            String transactionStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundedAmountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inProgressRefundAmountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundableAmountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RefundResponse> refunds
    ) {
        public PaymentDetailView {
            refunds = refunds == null ? List.of() : List.copyOf(refunds);
        }
    }

    public record GrantView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID grantId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entitlementId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attempts,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsUsed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsReserved,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsRevoked,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsLeft,
            Instant revokedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant grantedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String grantedBy
    ) {
    }

    public record HistoryView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            String fromState,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toState,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actor,
            String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at
    ) {
    }

    public record AuditEventItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
            UUID actorId,
            String actorUsername,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> actorRoles,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditActorType actorType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String module,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,
            String targetType,
            String targetId,
            String targetLabel,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditOutcome outcome,
            String errorCode,
            String traceId,
            String ip
    ) {
        public AuditEventItem {
            actorRoles = actorRoles == null ? List.of() : List.copyOf(actorRoles);
        }

        public static AuditEventItem from(AuditEventView view) {
            return new AuditEventItem(
                    view.id(),
                    view.occurredAt(),
                    view.actorId(),
                    view.actorUsername(),
                    view.actorRoles(),
                    view.actorType(),
                    view.module(),
                    view.action(),
                    view.targetType(),
                    view.targetId(),
                    view.targetLabel(),
                    view.before(),
                    view.after(),
                    view.reason(),
                    view.outcome(),
                    view.errorCode(),
                    view.traceId(),
                    view.ip());
        }
    }
}
