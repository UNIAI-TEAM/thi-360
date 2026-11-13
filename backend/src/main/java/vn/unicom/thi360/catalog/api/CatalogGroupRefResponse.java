package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Danh mục liên kết của nhóm cây")
public record CatalogGroupRefResponse(
        @Schema(description = "ID nhóm được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID refGroupId,
        @Schema(description = "Mã nhóm được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên tiếng Việt nhóm được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh nhóm được liên kết")
        String nameEn,
        @Schema(description = "Trạng thái nhóm được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "Thứ tự hiển thị liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        int position,
        @Schema(description = "Liên kết mặc định của cây hệ thống (không thể bỏ)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isDefault,
        @Schema(description = "Số nút trong cây đang có liên kết tới giá trị của nhóm này", requiredMode = Schema.RequiredMode.REQUIRED)
        long linkedItemCount
) {
}
