package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Nguồn gốc cấp quyền cho tài khoản")
public record PermissionSourceResponse(
        @Schema(description = "Loại nguồn (ROLE hoặc DIRECT)", requiredMode = Schema.RequiredMode.REQUIRED)
        PermissionSourceType type,
        @Schema(description = "ID vai trò (khi type = ROLE)")
        UUID roleId,
        @Schema(description = "Mã vai trò (khi type = ROLE)")
        String roleCode,
        @Schema(description = "Tên vai trò (khi type = ROLE)")
        String roleName,
        @Schema(description = "ID người gán (khi type = DIRECT)")
        UUID grantedBy,
        @Schema(description = "Tên đăng nhập người gán (khi type = DIRECT)")
        String grantedByUsername,
        @Schema(description = "Thời điểm gán trực tiếp (khi type = DIRECT)")
        Instant grantedAt,
        @Schema(description = "Lý do gán trực tiếp (khi type = DIRECT)")
        String reason
) {
    public static PermissionSourceResponse role(UUID roleId, String roleCode, String roleName) {
        return new PermissionSourceResponse(
                PermissionSourceType.ROLE, roleId, roleCode, roleName, null, null, null, null);
    }

    public static PermissionSourceResponse direct(
            UUID grantedBy, String grantedByUsername, Instant grantedAt, String reason) {
        return new PermissionSourceResponse(
                PermissionSourceType.DIRECT, null, null, null, grantedBy, grantedByUsername, grantedAt, reason);
    }
}
