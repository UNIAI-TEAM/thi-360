package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu cập nhật danh sách quyền gán trực tiếp của tài khoản")
public record UpdateUserPermissionsRequest(
        @Schema(description = "Danh sách mã quyền trực tiếp mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.user.permissionCodes.notNull}")
        List<String> permissionCodes,
        @Schema(description = "Lý do gán quyền trực tiếp (bắt buộc khi thêm quyền mới)")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason
) {
}
