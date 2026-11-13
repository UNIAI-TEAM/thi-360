package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Tổng hợp quyền hiệu lực và quyền trực tiếp tạm không hiệu lực của tài khoản")
public record EffectivePermissionsResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Phạm vi truy cập hiệu lực", requiredMode = Schema.RequiredMode.REQUIRED)
        Access effectiveAccess,
        @Schema(description = "Có vai trò MANAGE đang hoạt động hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasManageRole,
        @Schema(description = "Danh sách quyền đang có hiệu lực kèm nguồn", requiredMode = Schema.RequiredMode.REQUIRED)
        List<EffectivePermissionItemResponse> permissions,
        @Schema(description = "Các quyền gán trực tiếp đang tạm không hiệu lực (khi không có vai trò MANAGE)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserDirectPermissionItemResponse> inactiveDirectPermissions
) {
}
