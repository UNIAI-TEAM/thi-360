package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Yêu cầu tạo vai trò mới")
public record CreateRoleRequest(
        @Schema(description = "Mã vai trò duy nhất", example = "REVIEWER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.role.code.notBlank}")
        @Size(max = 64, message = "{validation.role.code.size}")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "{validation.role.code.pattern}")
        String code,
        @Schema(description = "Tên hiển thị vai trò", example = "Kiểm duyệt viên", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.role.name.notBlank}")
        @Size(max = 150, message = "{validation.role.name.size}")
        String name,
        @Schema(description = "Mô tả vai trò")
        @Size(max = 500, message = "{validation.role.description.size}")
        String description,
        @Schema(description = "Phạm vi truy cập bắt buộc", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.role.access.notNull}")
        Access access,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        RoleStatus status,
        @Schema(description = "Danh sách mã quyền ban đầu")
        List<String> permissionCodes
) {
}
