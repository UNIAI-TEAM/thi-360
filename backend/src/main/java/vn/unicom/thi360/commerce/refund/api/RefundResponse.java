package vn.unicom.thi360.commerce.refund.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.refund.domain.Refund;
import vn.unicom.thi360.commerce.refund.domain.RefundKind;
import vn.unicom.thi360.commerce.refund.domain.RefundState;

/** Thông tin chi tiết một yêu cầu hoàn tiền (TM 9.4). */
public record RefundResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID paymentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String paymentTxnRef,
        String vnpRequestId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RefundKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RefundState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean verifiedSystemError,
        String externalRef,
        String gatewayMessage,
        String markFailedEvidence,
        String markFailedReason,
        String vnpResponseCode,
        String vnpTransactionStatus,
        String vnpTransactionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestedBy,
        UUID requestedById,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
        Instant finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

    public static RefundResponse from(Refund refund, PurchaseOrder order, Payment payment) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                order.getCode(),
                payment.getTxnRef(),
                refund.getVnpRequestId(),
                refund.getAmountVnd(),
                refund.getKind(),
                refund.getState(),
                refund.getReason(),
                refund.isVerifiedSystemError(),
                refund.getExternalRef(),
                refund.getGatewayMessage(),
                refund.getMarkFailedEvidence(),
                refund.getMarkFailedReason(),
                refund.getVnpResponseCode(),
                refund.getVnpTransactionStatus(),
                refund.getVnpTransactionNo(),
                refund.getRequestedBy(),
                refund.getRequestedById(),
                refund.getRequestedAt(),
                refund.getFinishedAt(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }
}
