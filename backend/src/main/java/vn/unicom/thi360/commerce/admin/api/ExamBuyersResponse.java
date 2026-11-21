package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Trang danh sách người mua theo bài thi (TM 9.3, AC-6). */
public record ExamBuyersResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExamBuyerRow> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalBuyers
) {
    public ExamBuyersResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
