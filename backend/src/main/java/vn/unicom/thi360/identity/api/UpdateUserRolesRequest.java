package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu thay thế toàn bộ danh sách vai trò của tài khoản")
public record UpdateUserRolesRequest(
        @Schema(description = "Danh sách ID vai trò mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Danh sách vai trò không được để trống")
        List<UUID> roleIds
) {
}
