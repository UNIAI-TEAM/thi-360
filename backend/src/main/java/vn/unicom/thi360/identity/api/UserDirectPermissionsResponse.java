package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Danh sách quyền gán trực tiếp của tài khoản")
public record UserDirectPermissionsResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Tài khoản đang có ít nhất một vai trò MANAGE hoạt động hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasManageRole,
        @Schema(description = "Danh sách quyền gán trực tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserDirectPermissionItemResponse> permissions
) {
}
