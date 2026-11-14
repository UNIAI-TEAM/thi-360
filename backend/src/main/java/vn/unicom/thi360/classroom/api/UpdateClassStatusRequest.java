package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.unicom.thi360.classroom.domain.ClassStatus;

@Schema(description = "Yêu cầu thay đổi trạng thái lớp học")
public record UpdateClassStatusRequest(
        @Schema(description = "Trạng thái lớp học mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Trạng thái lớp học là bắt buộc")
        ClassStatus status,
        @Schema(description = "Lý do thay đổi trạng thái")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phiên bản cập nhật là bắt buộc")
        Long version
) {
}
