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
        @Size(max = 100, message = "Mã nhóm danh mục tối đa 50 ký tự sau khi bỏ khoảng trắng")
        String code,
        @Schema(description = "Tên tiếng Việt mới")
        @Size(max = 255, message = "Tên nhóm danh mục tối đa 255 ký tự")
        String nameVi,
        @Schema(description = "Tên tiếng Anh mới")
        @Size(max = 255, message = "Tên tiếng Anh tối đa 255 ký tự")
        String nameEn,
        @Schema(description = "Mô tả mới")
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,
        @Schema(description = "Trạng thái mới")
        CatalogStatus status,
        @Schema(description = "Tên các cấp mới (chỉ cho nhóm TREE; đổi số lượng cấp bị khóa khi đã có giá trị)")
        List<String> levels,
        @Schema(description = "Danh sách ID nhóm danh mục liên kết mới (chỉ cho nhóm TREE)")
        List<UUID> refGroupIds,
        @Schema(description = "Lý do ngừng sử dụng hoặc cập nhật (bắt buộc khi chuyển sang INACTIVE, ghi nhật ký)")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phiên bản cập nhật là bắt buộc")
        Long version
) {
}
