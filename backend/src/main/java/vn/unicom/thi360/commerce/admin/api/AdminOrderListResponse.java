package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Trang danh sách đơn hàng quản trị kèm số đếm dải "Cần xử lý" (TM 9.1, BS §14). */
public record AdminOrderListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminOrderSummary> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderAttentionSummary attention
) {
    public AdminOrderListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
