package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.UserStatus;

@Schema(description = "Tài khoản đang sở hữu quyền kèm các nguồn quyền")
public record PermissionUserResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Mã tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Họ và tên", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Email")
        String email,
        @Schema(description = "Trạng thái tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status,
        @Schema(description = "Danh sách nguồn cấp quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PermissionSourceResponse> sources
) {
}
