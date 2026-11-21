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

/** Tóm tắt một đơn hàng trong danh sách quản trị (TM 9.1, 16.4). */
public record AdminOrderSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID buyerUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerFullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
        UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID bundleId,
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
        Instant fulfilledAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<AdminOrderSummaryItem> items
) {
    public AdminOrderSummary(
            UUID orderId,
            String code,
            UUID buyerUserId,
            String buyerUsername,
            String buyerFullName,
            ProductType productType,
            UUID examId,
            String productCode,
            String productName,
            long listPriceVnd,
            long amountDueVnd,
            PaymentMethod paymentMethod,
            OrderState orderState,
            AdminPaymentStatus paymentStatus,
            AdminRefundStatus refundStatus,
            boolean needsReview,
            List<OrderAttentionType> attentionTypes,
            Instant createdAt,
            Instant paidAt,
            Instant fulfilledAt) {
        this(orderId, code, buyerUserId, buyerUsername, buyerFullName, productType, examId, null,
                productCode, productName, listPriceVnd, amountDueVnd, paymentMethod, orderState,
                paymentStatus, refundStatus, needsReview, attentionTypes, createdAt, paidAt, fulfilledAt,
                List.of());
    }

    public AdminOrderSummary {
        attentionTypes = attentionTypes == null ? List.of() : List.copyOf(attentionTypes);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
