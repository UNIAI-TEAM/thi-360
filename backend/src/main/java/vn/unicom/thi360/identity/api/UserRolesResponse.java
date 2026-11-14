package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Danh sách vai trò của tài khoản")
public record UserRolesResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Phạm vi truy cập hiệu lực hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Access access,
        @Schema(description = "Các vai trò đang giữ", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserRoleItemResponse> roles
) {
}
