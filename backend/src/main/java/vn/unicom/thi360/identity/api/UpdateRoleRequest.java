package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Yêu cầu cập nhật vai trò")
public record UpdateRoleRequest(
        @Schema(description = "Mã vai trò mới (không đổi được đối với vai trò hệ thống)")
        @Size(max = 64, message = "Mã vai trò tối đa 64 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Mã vai trò chỉ gồm chữ cái, số và ký tự _ . -")
        String code,
        @Schema(description = "Tên hiển thị vai trò")
        @Size(max = 150, message = "Tên vai trò tối đa 150 ký tự")
        String name,
        @Schema(description = "Mô tả vai trò")
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,
        @Schema(description = "Phạm vi truy cập (chỉ đổi được khi không phải vai trò hệ thống và chưa có người dùng giữ)")
        Access access,
        @Schema(description = "Trạng thái vai trò")
        RoleStatus status,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Phiên bản cập nhật là bắt buộc")
        Long version
) {
}
