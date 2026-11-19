package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu gửi liên kết đặt lại mật khẩu")
public record PasswordForgotRequest(
        @Schema(description = "Email của tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.forgot.email.notBlank}")
        @Size(max = 255, message = "{validation.user.email.size}")
        String email
) {
}
