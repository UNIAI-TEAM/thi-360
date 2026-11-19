package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu đặt lại mật khẩu bằng mã trong thư")
public record PasswordResetRequest(
        @Schema(description = "Mã đặt lại trong liên kết thư", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.reset.token.notBlank}")
        String token,

        @Schema(description = "Mật khẩu mới đạt quy tắc bảo mật", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.new.notBlank}")
        String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetRequest["
                + "token=" + (token == null ? "null" : "[REDACTED]")
                + ", newPassword=" + (newPassword == null ? "null" : "[REDACTED]")
                + ']';
    }
}
