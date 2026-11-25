package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu đăng nhập từ ứng dụng di động")
public record MobileLoginRequest(
        @Schema(description = "Tên đăng nhập hoặc email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.login.identifier.notBlank}")
        String identifier,

        @Schema(description = "Mật khẩu", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.login.password.notBlank}")
        String password
) {
    @Override
    public String toString() {
        return "MobileLoginRequest["
                + "identifier=" + identifier
                + ", password=" + (password == null ? "null" : "[REDACTED]")
                + ']';
    }
}
