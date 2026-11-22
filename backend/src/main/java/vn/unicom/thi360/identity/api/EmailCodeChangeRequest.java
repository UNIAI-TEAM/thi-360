package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu đổi email: gửi mã xác nhận tới email mới")
public record EmailCodeChangeRequest(
        @Schema(description = "Email mới", example = "moi@thi360.vn", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.user.email.notBlank}")
        @Size(max = 255, message = "{validation.user.email.size}")
        String newEmail,

        @Schema(description = "Mật khẩu hiện tại", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.current.notBlank}")
        String currentPassword
) {
    @Override
    public String toString() {
        return "EmailCodeChangeRequest["
                + "newEmail=" + newEmail
                + ", currentPassword=" + (currentPassword == null ? "null" : "[REDACTED]")
                + ']';
    }
}
