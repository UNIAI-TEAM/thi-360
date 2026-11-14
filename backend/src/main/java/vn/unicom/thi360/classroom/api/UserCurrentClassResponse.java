package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.classroom.domain.ClassStatus;

@Schema(description = "Lớp học hiện tại của tài khoản")
public record UserCurrentClassResponse(
        @Schema(description = "ID lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classId,
        @Schema(description = "Mã lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String classCode,
        @Schema(description = "Tên lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String className,
        @Schema(description = "Trạng thái lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        ClassStatus classStatus,
        @Schema(description = "Thời điểm tham gia lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant joinedAt,
        @Schema(description = "ID người thêm vào lớp")
        UUID addedBy,
        @Schema(description = "Tên đăng nhập người thêm vào lớp")
        String addedByUsername
) {
}
