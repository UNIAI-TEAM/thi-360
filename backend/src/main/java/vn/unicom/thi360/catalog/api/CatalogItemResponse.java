package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Chi tiết giá trị danh mục")
public record CatalogItemResponse(
        @Schema(description = "ID giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "ID nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID groupId,
        @Schema(description = "Mã nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Mã giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Số thứ tự giá trị bất biến do hệ thống sinh", requiredMode = Schema.RequiredMode.REQUIRED)
        int value,
        @Schema(description = "Tên tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh")
        String nameEn,
        @Schema(description = "Mô tả")
        String description,
        @Schema(description = "Thứ tự hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        int displayOrder,
        @Schema(description = "Trạng thái riêng của giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "Trạng thái hiệu lực (tính cả nhóm và tổ tiên)", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus effectiveStatus,
        @Schema(description = "Cờ giá trị hệ thống", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean system,
        @Schema(description = "ID nút cha (với nhóm TREE)")
        UUID parentId,
        @Schema(description = "Cấp trong cây (1 = gốc)", requiredMode = Schema.RequiredMode.REQUIRED)
        short depth,
        @Schema(description = "Đường dẫn id từ gốc tới chính nút", requiredMode = Schema.RequiredMode.REQUIRED)
        String path,
        @Schema(description = "Số nút con trực tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
        long childCount,
        @Schema(description = "Danh sách liên kết riêng của chính nút", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogItemRefResponse> ownRefs,
        @Schema(description = "Danh sách liên kết kế thừa từ tổ tiên", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogInheritedRefResponse> inheritedRefs,
        @Schema(description = "Thông tin phiên bản hiện hành của mã tổ hợp (chỉ có với nhóm COMBINATION)")
        CombinationVersionResponse combination,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
    public CatalogItemResponse {
        ownRefs = ownRefs == null ? List.of() : List.copyOf(ownRefs);
        inheritedRefs = inheritedRefs == null ? List.of() : List.copyOf(inheritedRefs);
    }
}
