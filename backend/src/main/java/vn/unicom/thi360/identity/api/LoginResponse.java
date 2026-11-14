package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả đăng nhập hoặc làm mới phiên")
public record LoginResponse(
        @Schema(description = "Đường dẫn chuyển hướng sau đăng nhập", example = "http://localhost:3000/home", requiredMode = Schema.RequiredMode.REQUIRED)
        String redirectTo,

        @Schema(description = "Cần đổi mật khẩu ngay", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword
) {
}
