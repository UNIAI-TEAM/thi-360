package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Thông tin tóm tắt vai trò")
public record RoleSummaryResponse(
        @Schema(description = "ID vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mô tả vai trò")
        String description,
        @Schema(description = "Phạm vi truy cập", requiredMode = Schema.RequiredMode.REQUIRED)
        Access access,
        @Schema(description = "Vai trò hệ thống mặc định", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean system,
        @Schema(description = "Trạng thái vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        RoleStatus status,
        @Schema(description = "Số lượng quyền thuộc vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        long permissionCount,
        @Schema(description = "Số lượng tài khoản đang giữ vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        long userCount,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
}
