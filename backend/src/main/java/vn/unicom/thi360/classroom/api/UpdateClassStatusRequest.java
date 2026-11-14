package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.unicom.thi360.classroom.domain.ClassStatus;

@Schema(description = "Yêu cầu thay đổi trạng thái lớp học")
public record UpdateClassStatusRequest(
        @Schema(description = "Trạng thái lớp học mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.class.status.notNull}")
        ClassStatus status,
        @Schema(description = "Lý do thay đổi trạng thái")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.version.notNull}")
        Long version
) {
}
