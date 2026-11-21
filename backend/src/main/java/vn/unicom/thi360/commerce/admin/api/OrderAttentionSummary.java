package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Số lượng đơn theo từng loại cảnh báo của dải "Cần xử lý" (TM 9.1, BS §14). */
public record OrderAttentionSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lateSuccessPending,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long paidUnfulfilled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long paymentUnknownOver24h,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long duplicateUnrefunded,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long needsReview,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refundUnknown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalAttentionOrders
) {
}
