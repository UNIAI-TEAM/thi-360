package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Yêu cầu tạo giá trị danh mục mới")
public record CreateCatalogItemRequest(
        @Schema(description = "Mã giá trị duy nhất trong nhóm", example = "TOAN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.catalog.item.code.notBlank}")
        @Size(max = 100, message = "{validation.catalog.item.code.size}")
        String code,
        @Schema(description = "Tên tiếng Việt", example = "Toán học", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.catalog.item.nameVi.notBlank}")
        @Size(max = 255, message = "{validation.catalog.item.nameVi.size}")
        String nameVi,
        @Schema(description = "Tên tiếng Anh", example = "Mathematics")
        @Size(max = 255, message = "{validation.catalog.nameEn.size}")
        String nameEn,
        @Schema(description = "Mô tả")
        @Size(max = 1000, message = "{validation.catalog.description.size}")
        String description,
        @Schema(description = "Thứ tự hiển thị (không âm; trống thì xếp cuối)")
        @Min(value = 0, message = "{validation.catalog.item.displayOrder.min}")
        Integer displayOrder,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        CatalogStatus status,
        @Schema(description = "ID nút cha (chỉ dùng cho nhóm TREE)")
        UUID parentId,
        @Schema(description = "Danh sách ID giá trị liên kết riêng của nút (chỉ dùng cho nhóm TREE)")
        List<UUID> refItemIds,
        @Schema(description = "ID giá trị kỳ thi (bắt buộc cho nhóm COMBINATION)")
        UUID examItemId,
        @Schema(description = "Danh sách ID môn học theo thứ tự (bắt buộc cho nhóm COMBINATION, không trùng)")
        List<UUID> subjectItemIds,
        @Schema(description = "Ghi chú nguồn quy ước (tùy chọn cho nhóm COMBINATION)")
        @Size(max = 500, message = "Nguồn quy ước tối đa 500 ký tự")
        String sourceNote
) {
    public CreateCatalogItemRequest(
            String code,
            String nameVi,
            String nameEn,
            String description,
            Integer displayOrder,
            CatalogStatus status,
            UUID parentId,
            List<UUID> refItemIds) {
        this(code, nameVi, nameEn, description, displayOrder, status, parentId, refItemIds, null, null, null);
    }
}
