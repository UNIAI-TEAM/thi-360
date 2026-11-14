package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Lớp học hiện tại của tài khoản trong hồ sơ")
public record UserProfileCurrentClassResponse(
        @Schema(description = "ID lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classId,
        @Schema(description = "Mã lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String classCode,
        @Schema(description = "Tên lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String className,
        @Schema(description = "Trạng thái lớp học (ACTIVE, INACTIVE)", requiredMode = Schema.RequiredMode.REQUIRED)
        String classStatus,
        @Schema(description = "Thời điểm tham gia lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant joinedAt,
        @Schema(description = "ID người thêm vào lớp")
        UUID addedBy,
        @Schema(description = "Tên đăng nhập người thêm vào lớp")
        String addedByUsername
) {
}
