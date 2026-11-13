package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Nút trong danh mục dạng cây")
public record CatalogTreeNodeResponse(
        @Schema(description = "ID nút", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "ID nhóm cây", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID groupId,
        @Schema(description = "Mã nút", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Số thứ tự giá trị bất biến", requiredMode = Schema.RequiredMode.REQUIRED)
        int value,
        @Schema(description = "Tên tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh")
        String nameEn,
        @Schema(description = "Mô tả")
        String description,
        @Schema(description = "Thứ tự hiển thị trong cùng cha", requiredMode = Schema.RequiredMode.REQUIRED)
        int displayOrder,
        @Schema(description = "Trạng thái riêng của nút", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "Trạng thái hiệu lực (tính cả tổ tiên)", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus effectiveStatus,
        @Schema(description = "ID nút cha")
        UUID parentId,
        @Schema(description = "Cấp trong cây (1 = gốc)", requiredMode = Schema.RequiredMode.REQUIRED)
        short depth,
        @Schema(description = "Đường dẫn id từ gốc tới nút", requiredMode = Schema.RequiredMode.REQUIRED)
        String path,
        @Schema(description = "Số nút con trực tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
        long childCount,
        @Schema(description = "Có nút con trực tiếp hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasChildren,
        @Schema(description = "Nút khớp trực tiếp với từ khóa tìm kiếm (khi có q)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean matched,
        @Schema(description = "Danh sách liên kết riêng của nút", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogItemRefResponse> ownRefs,
        @Schema(description = "Danh sách liên kết kế thừa từ tổ tiên", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogInheritedRefResponse> inheritedRefs,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version
) {
    public CatalogTreeNodeResponse {
        ownRefs = ownRefs == null ? List.of() : List.copyOf(ownRefs);
        inheritedRefs = inheritedRefs == null ? List.of() : List.copyOf(inheritedRefs);
    }
}
