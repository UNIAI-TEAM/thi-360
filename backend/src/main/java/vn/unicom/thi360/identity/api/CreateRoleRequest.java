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
        @NotBlank(message = "Mã vai trò không được để trống")
        @Size(max = 64, message = "Mã vai trò tối đa 64 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Mã vai trò chỉ gồm chữ cái, số và ký tự _ . -")
        String code,
        @Schema(description = "Tên hiển thị vai trò", example = "Kiểm duyệt viên", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tên vai trò không được để trống")
        @Size(max = 150, message = "Tên vai trò tối đa 150 ký tự")
        String name,
        @Schema(description = "Mô tả vai trò")
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,
        @Schema(description = "Phạm vi truy cập bắt buộc", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phạm vi truy cập là bắt buộc")
        Access access,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        RoleStatus status,
        @Schema(description = "Danh sách mã quyền ban đầu")
        List<String> permissionCodes
) {
}
