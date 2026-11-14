package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Lựa chọn danh mục đang hoạt động cho module khác")
public record CatalogOptionResponse(
        @Schema(description = "ID giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Số thứ tự giá trị bất biến", requiredMode = Schema.RequiredMode.REQUIRED)
        int value,
        @Schema(description = "Tên tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh")
        String nameEn,
        @Schema(description = "Mô tả")
        String description,
        @Schema(description = "Thứ tự hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        int displayOrder
) {
}
