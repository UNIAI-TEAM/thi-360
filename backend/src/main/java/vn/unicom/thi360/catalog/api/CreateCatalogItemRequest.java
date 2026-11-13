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
        @NotBlank(message = "Mã giá trị không được để trống")
        @Size(max = 100, message = "Mã giá trị tối đa 50 ký tự sau khi bỏ khoảng trắng")
        String code,
        @Schema(description = "Tên tiếng Việt", example = "Toán học", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tên tiếng Việt không được để trống")
        @Size(max = 255, message = "Tên tiếng Việt tối đa 255 ký tự")
        String nameVi,
        @Schema(description = "Tên tiếng Anh", example = "Mathematics")
        @Size(max = 255, message = "Tên tiếng Anh tối đa 255 ký tự")
        String nameEn,
        @Schema(description = "Mô tả")
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,
        @Schema(description = "Thứ tự hiển thị (không âm; trống thì xếp cuối)")
        @Min(value = 0, message = "Thứ tự hiển thị phải lớn hơn hoặc bằng 0")
        Integer displayOrder,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        CatalogStatus status,
        @Schema(description = "ID nút cha (chỉ dùng cho nhóm TREE)")
        UUID parentId,
        @Schema(description = "Danh sách ID giá trị liên kết riêng của nút (chỉ dùng cho nhóm TREE)")
        List<UUID> refItemIds
) {
}
