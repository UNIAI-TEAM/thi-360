package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Kết quả sau khi xác nhận nhập danh mục từ Excel")
public record CatalogImportResultResponse(
        @Schema(description = "ID phiên xem trước đã xác nhận", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID previewId,
        @Schema(description = "ID nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID groupId,
        @Schema(description = "Mã nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Tổng số dòng dữ liệu", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalRows,
        @Schema(description = "Số giá trị mới được tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        int createdCount,
        @Schema(description = "Số nút sẵn có được dùng lại (đối với nhóm TREE)", requiredMode = Schema.RequiredMode.REQUIRED)
        int reusedCount
) {
}
