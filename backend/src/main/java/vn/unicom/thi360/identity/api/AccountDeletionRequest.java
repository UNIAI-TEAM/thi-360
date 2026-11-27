package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu học viên tự xóa tài khoản")
public record AccountDeletionRequest(
        @Schema(description = "Mật khẩu hiện tại", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.current.notBlank}")
        String currentPassword
) {
    @Override
    public String toString() {
        return "AccountDeletionRequest[currentPassword=" + (currentPassword == null ? "null" : "[REDACTED]") + ']';
    }
}
