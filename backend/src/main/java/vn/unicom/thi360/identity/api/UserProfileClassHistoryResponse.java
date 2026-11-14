package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bản ghi lịch sử tham gia, rời hoặc chuyển lớp trong hồ sơ tài khoản")
public record UserProfileClassHistoryResponse(
        @Schema(description = "ID bản ghi lịch sử", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "ID lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classId,
        @Schema(description = "Mã lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String classCode,
        @Schema(description = "Tên lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String className,
        @Schema(description = "Hành động (JOIN, LEAVE, TRANSFER_OUT, TRANSFER_IN)", requiredMode = Schema.RequiredMode.REQUIRED)
        String action,
        @Schema(description = "ID lớp liên quan (khi chuyển lớp)")
        UUID relatedClassId,
        @Schema(description = "Mã lớp liên quan")
        String relatedClassCode,
        @Schema(description = "Tên lớp liên quan")
        String relatedClassName,
        @Schema(description = "ID người thực hiện")
        UUID actorId,
        @Schema(description = "Tên đăng nhập người thực hiện")
        String actorUsername,
        @Schema(description = "Lý do")
        String reason,
        @Schema(description = "Thời điểm phát sinh", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant occurredAt
) {
}
