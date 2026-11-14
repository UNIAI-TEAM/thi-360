package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Yêu cầu cập nhật giá trị danh mục")
public record UpdateCatalogItemRequest(
        @Schema(description = "Mã giá trị mới")
        @Size(max = 100, message = "{validation.catalog.item.code.size}")
        String code,
        @Schema(description = "Tên tiếng Việt mới")
        @Size(max = 255, message = "{validation.catalog.item.nameVi.size}")
        String nameVi,
        @Schema(description = "Tên tiếng Anh mới")
        @Size(max = 255, message = "{validation.catalog.nameEn.size}")
        String nameEn,
        @Schema(description = "Mô tả mới")
        @Size(max = 1000, message = "{validation.catalog.description.size}")
        String description,
        @Schema(description = "Thứ tự hiển thị mới")
        @Min(value = 0, message = "{validation.catalog.item.displayOrder.min}")
        Integer displayOrder,
        @Schema(description = "Trạng thái mới")
        CatalogStatus status,
        @Schema(description = "Danh sách ID giá trị liên kết riêng mới (chỉ cho nhóm TREE; nếu truyền sẽ thay toàn bộ liên kết của nút)")
        List<UUID> refItemIds,
        @Schema(description = "Lý do ngừng sử dụng hoặc cập nhật (ghi nhật ký)")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.version.notNull}")
        Long version,
        @Schema(description = "ID giá trị kỳ thi mới (chỉ cho nhóm COMBINATION)")
        UUID examItemId,
        @Schema(description = "Danh sách ID môn học mới theo thứ tự (chỉ cho nhóm COMBINATION)")
        List<UUID> subjectItemIds,
        @Schema(description = "Ghi chú nguồn quy ước mới (chỉ cho nhóm COMBINATION)")
        @Size(max = 500, message = "{validation.catalog.combination.sourceNote.size}")
        String sourceNote
) {
    public UpdateCatalogItemRequest(
            String code,
            String nameVi,
            String nameEn,
            String description,
            Integer displayOrder,
            CatalogStatus status,
            List<UUID> refItemIds,
            String reason,
            Long version) {
        this(code, nameVi, nameEn, description, displayOrder, status, refItemIds, reason, version, null, null, null);
    }
}
