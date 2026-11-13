package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Thông tin nhóm danh mục")
public record CatalogGroupResponse(
        @Schema(description = "ID nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên tiếng Việt của nhóm", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh của nhóm")
        String nameEn,
        @Schema(description = "Mô tả nhóm danh mục")
        String description,
        @Schema(description = "Trạng thái nhóm", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "Loại nhóm (SYSTEM/CUSTOM)", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogGroupType type,
        @Schema(description = "Kiểu danh mục (SIMPLE/COMBINATION/TREE)", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogGroupKind kind,
        @Schema(description = "Tên các cấp từ gốc tới lá (chỉ có với kiểu TREE)")
        List<String> levels,
        @Schema(description = "Cờ nhóm hệ thống", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean system,
        @Schema(description = "Tổng số giá trị trong nhóm", requiredMode = Schema.RequiredMode.REQUIRED)
        long itemCount,
        @Schema(description = "Danh sách danh mục liên kết (chỉ có với kiểu TREE)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogGroupRefResponse> refGroups,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
    public CatalogGroupResponse {
        levels = levels == null ? null : List.copyOf(levels);
        refGroups = refGroups == null ? List.of() : List.copyOf(refGroups);
    }
}
