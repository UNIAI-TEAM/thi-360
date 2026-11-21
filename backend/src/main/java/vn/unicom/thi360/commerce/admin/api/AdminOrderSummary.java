package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.admin.domain.AdminPaymentStatus;
import vn.unicom.thi360.commerce.admin.domain.AdminRefundStatus;
import vn.unicom.thi360.commerce.admin.domain.OrderAttentionType;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.domain.ProductType;

/** Tóm tắt một đơn hàng trong danh sách quản trị (TM 9.1). */
public record AdminOrderSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID buyerUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerFullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
        UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPriceVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountDueVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentMethod paymentMethod,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderState orderState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminPaymentStatus paymentStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminRefundStatus refundStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsReview,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderAttentionType> attentionTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        Instant paidAt,
        Instant fulfilledAt
) {
    public AdminOrderSummary {
        attentionTypes = attentionTypes == null ? List.of() : List.copyOf(attentionTypes);
    }
}
