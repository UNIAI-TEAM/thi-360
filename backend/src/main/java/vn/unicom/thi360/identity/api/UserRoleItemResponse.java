package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Vai trò đang gán cho tài khoản")
public record UserRoleItemResponse(
        @Schema(description = "ID vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID roleId,
        @Schema(description = "Mã vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String roleCode,
        @Schema(description = "Tên vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String roleName,
        @Schema(description = "Phạm vi truy cập", requiredMode = Schema.RequiredMode.REQUIRED)
        Access access,
        @Schema(description = "Trạng thái vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        RoleStatus status,
        @Schema(description = "Vai trò hệ thống", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean system,
        @Schema(description = "ID người gán")
        UUID assignedBy,
        @Schema(description = "Thời điểm gán", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant assignedAt
) {
}
