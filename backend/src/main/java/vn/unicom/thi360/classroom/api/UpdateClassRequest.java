package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Yêu cầu cập nhật thông tin lớp học")
public record UpdateClassRequest(
        @Schema(description = "Mã lớp học mới")
        @Size(max = 100, message = "Mã lớp tối đa 30 ký tự sau khi bỏ khoảng trắng")
        String code,
        @Schema(description = "Tên lớp học mới")
        @Size(max = 200, message = "Tên lớp tối đa 200 ký tự")
        String name,
        @Schema(description = "Mô tả lớp học mới (chuỗi rỗng để xóa mô tả)")
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,
        @Schema(description = "ID người phụ trách mới")
        UUID ownerUserId,
        @Schema(description = "Xóa người phụ trách hiện tại khi đặt là true")
        Boolean clearOwner,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phiên bản cập nhật là bắt buộc")
        Long version
) {
}
