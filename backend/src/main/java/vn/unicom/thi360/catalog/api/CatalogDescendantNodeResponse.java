package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Nút con cháu hiệu lực trong cây danh mục")
public record CatalogDescendantNodeResponse(
        @Schema(description = "ID nút", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã nút", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Số thứ tự giá trị bất biến", requiredMode = Schema.RequiredMode.REQUIRED)
        int value,
        @Schema(description = "Tên tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh")
        String nameEn,
        @Schema(description = "ID nút cha")
        UUID parentId,
        @Schema(description = "Cấp trong cây", requiredMode = Schema.RequiredMode.REQUIRED)
        short depth,
        @Schema(description = "Đường dẫn id từ gốc tới nút", requiredMode = Schema.RequiredMode.REQUIRED)
        String path
) {
}
