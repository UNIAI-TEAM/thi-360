package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Tóm tắt bài thi thành phần trong một dòng danh sách đơn hàng quản trị (TM 16.4). */
public record AdminOrderSummaryItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position
) {
}
