package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả đăng ký: phiên đã được tạo (cookie HttpOnly)")
public record RegisterResponse(
        @Schema(description = "Đường dẫn chuyển hướng sau đăng ký: callbackUrl hợp lệ hoặc /home",
                example = "http://localhost:3000/home", requiredMode = Schema.RequiredMode.REQUIRED)
        String redirectTo
) {
}
