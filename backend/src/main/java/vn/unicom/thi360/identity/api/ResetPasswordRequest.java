package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu đặt lại mật khẩu tài khoản")
public record ResetPasswordRequest(
        @Schema(description = "Mật khẩu mới đạt quy tắc bảo mật", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,
        @Schema(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập kế tiếp (mặc định false)")
        Boolean mustChangePassword,
        @Schema(description = "Lý do đặt lại mật khẩu")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
