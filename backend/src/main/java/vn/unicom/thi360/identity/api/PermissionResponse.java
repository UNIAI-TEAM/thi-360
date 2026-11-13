package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Thông tin quyền hệ thống")
public record PermissionResponse(
        @Schema(description = "ID quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã quyền cố định", example = "USER.VIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Chỉ số bit cố định", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int bitIndex,
        @Schema(description = "Phạm vi quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        Access scope,
        @Schema(description = "Tên hiển thị tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mã đối tượng nhóm quyền", example = "USER", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Tên đối tượng nhóm quyền", example = "Tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupName,
        @Schema(description = "Mô tả chi tiết quyền")
        String description,
        @Schema(description = "Số vai trò đang chứa quyền này", requiredMode = Schema.RequiredMode.REQUIRED)
        long roleCount,
        @Schema(description = "Số tài khoản đang có hiệu lực quyền này", requiredMode = Schema.RequiredMode.REQUIRED)
        long userCount
) {
}
