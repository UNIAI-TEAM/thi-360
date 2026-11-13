package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Quyền được gán trực tiếp cho tài khoản")
public record UserDirectPermissionItemResponse(
        @Schema(description = "ID quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID permissionId,
        @Schema(description = "Mã quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        String permissionCode,
        @Schema(description = "Tên quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        String permissionName,
        @Schema(description = "Mã nhóm đối tượng", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Tên nhóm đối tượng", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupName,
        @Schema(description = "ID người gán")
        UUID grantedBy,
        @Schema(description = "Tên đăng nhập người gán")
        String grantedByUsername,
        @Schema(description = "Thời điểm gán", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant grantedAt,
        @Schema(description = "Lý do gán trực tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        @Schema(description = "Có đang có hiệu lực hay tạm không hiệu lực do thiếu vai trò MANAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean effective
) {
}
