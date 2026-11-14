package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Yêu cầu cập nhật nhóm danh mục")
public record UpdateCatalogGroupRequest(
        @Schema(description = "Mã nhóm mới")
        @Size(max = 100, message = "{validation.catalog.group.code.size}")
        String code,
        @Schema(description = "Tên tiếng Việt mới")
        @Size(max = 255, message = "{validation.catalog.group.nameVi.size}")
        String nameVi,
        @Schema(description = "Tên tiếng Anh mới")
        @Size(max = 255, message = "{validation.catalog.nameEn.size}")
        String nameEn,
        @Schema(description = "Mô tả mới")
        @Size(max = 1000, message = "{validation.catalog.description.size}")
        String description,
        @Schema(description = "Trạng thái mới")
        CatalogStatus status,
        @Schema(description = "Tên các cấp mới (chỉ cho nhóm TREE; đổi số lượng cấp bị khóa khi đã có giá trị)")
        List<String> levels,
        @Schema(description = "Danh sách ID nhóm danh mục liên kết mới (chỉ cho nhóm TREE)")
        List<UUID> refGroupIds,
        @Schema(description = "Lý do ngừng sử dụng hoặc cập nhật (bắt buộc khi chuyển sang INACTIVE, ghi nhật ký)")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.version.notNull}")
        Long version
) {
}
