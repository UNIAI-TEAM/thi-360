package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu đăng nhập")
public record LoginRequest(
        @Schema(description = "Tên đăng nhập hoặc email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tên đăng nhập hoặc email là bắt buộc")
        String identifier,

        @Schema(description = "Mật khẩu", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Mật khẩu là bắt buộc")
        String password,

        @Schema(description = "Đường dẫn chuyển hướng sau đăng nhập", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String callbackUrl
) {
}
