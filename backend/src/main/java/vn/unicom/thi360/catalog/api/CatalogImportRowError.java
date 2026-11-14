package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lỗi kiểm tra dữ liệu của một dòng trong tệp Excel")
public record CatalogImportRowError(
        @Schema(description = "Số dòng trong tệp Excel (1-based, dòng tiêu đề là 1)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int rowNo,
        @Schema(description = "Tên cột hoặc trường dữ liệu bị lỗi", example = "code", requiredMode = Schema.RequiredMode.REQUIRED)
        String column,
        @Schema(description = "Mã lỗi ổn định", example = "CATALOG_CODE_TAKEN", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Thông báo chi tiết", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
