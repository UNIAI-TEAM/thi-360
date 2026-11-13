package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả xem trước thao tác hàng loạt trên tài khoản")
public record BulkUserActionPreviewResponse(
        @Schema(description = "Tổng số tài khoản gửi lên (đã loại trùng)", requiredMode = Schema.RequiredMode.REQUIRED)
        int total,
        @Schema(description = "Số tài khoản đủ điều kiện thực hiện", requiredMode = Schema.RequiredMode.REQUIRED)
        int affected,
        @Schema(description = "Danh sách tài khoản bị bỏ qua kèm lý do", requiredMode = Schema.RequiredMode.REQUIRED)
        List<BulkUserSkippedItem> skipped
) {
}
