package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.classroom.domain.ClassStatus;

@Schema(description = "Thông tin tóm tắt lớp học")
public record ClassSummaryResponse(
        @Schema(description = "ID lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mô tả lớp học")
        String description,
        @Schema(description = "ID người phụ trách")
        UUID ownerUserId,
        @Schema(description = "Tên đăng nhập người phụ trách")
        String ownerUsername,
        @Schema(description = "Họ tên người phụ trách")
        String ownerFullName,
        @Schema(description = "Trạng thái lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        ClassStatus status,
        @Schema(description = "Số lượng học viên hiện tại trong lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        long memberCount,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
}
