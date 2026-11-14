package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;

@Schema(description = "Kết quả xem trước khi nhập danh mục từ Excel")
public record CatalogImportPreviewResponse(
        @Schema(description = "ID phiên xem trước để dùng khi xác nhận", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID previewId,
        @Schema(description = "ID nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID groupId,
        @Schema(description = "Mã nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Kiểu nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogGroupKind groupKind,
        @Schema(description = "Tổng số dòng dữ liệu đọc được", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalRows,
        @Schema(description = "Số dòng hợp lệ", requiredMode = Schema.RequiredMode.REQUIRED)
        int validRows,
        @Schema(description = "Số dòng có lỗi", requiredMode = Schema.RequiredMode.REQUIRED)
        int errorRows,
        @Schema(description = "Có thể xác nhận nhập hay không (true khi totalRows > 0 và errorRows == 0)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean canConfirm,
        @Schema(description = "Danh sách lỗi theo dòng", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogImportRowError> errors
) {
    public CatalogImportPreviewResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
