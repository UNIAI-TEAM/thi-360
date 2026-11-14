package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Yêu cầu tạo nhóm danh mục tùy chỉnh mới")
public record CreateCatalogGroupRequest(
        @Schema(description = "Mã nhóm duy nhất (theo mẫu ^[A-Za-z0-9][A-Za-z0-9_.-]{0,49}$)", example = "TOPIC", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.catalog.group.code.notBlank}")
        @Size(max = 100, message = "{validation.catalog.group.code.size}")
        String code,
        @Schema(description = "Tên tiếng Việt của nhóm", example = "Chủ đề", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.catalog.group.nameVi.notBlank}")
        @Size(max = 255, message = "{validation.catalog.group.nameVi.size}")
        String nameVi,
        @Schema(description = "Tên tiếng Anh của nhóm", example = "Topic")
        @Size(max = 255, message = "{validation.catalog.nameEn.size}")
        String nameEn,
        @Schema(description = "Mô tả nhóm danh mục")
        @Size(max = 1000, message = "{validation.catalog.description.size}")
        String description,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        CatalogStatus status,
        @Schema(description = "Kiểu danh mục: SIMPLE (mặc định) hoặc TREE")
        CatalogGroupKind kind,
        @Schema(description = "Danh sách tên cấp từ gốc tới lá (2–6 cấp, bắt buộc khi kind = TREE)")
        List<String> levels,
        @Schema(description = "Danh sách ID nhóm danh mục SIMPLE liên kết (chỉ áp dụng khi kind = TREE)")
        List<UUID> refGroupIds
) {
}
