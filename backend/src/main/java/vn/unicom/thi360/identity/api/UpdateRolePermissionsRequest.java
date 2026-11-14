package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Yêu cầu cập nhật toàn bộ danh sách quyền của vai trò")
public record UpdateRolePermissionsRequest(
        @Schema(description = "Danh sách mã quyền mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.role.permissionCodes.notNull}")
        List<String> permissionCodes,
        @Schema(description = "Phiên bản khóa lạc quan tùy chọn")
        Long version
) {
}
