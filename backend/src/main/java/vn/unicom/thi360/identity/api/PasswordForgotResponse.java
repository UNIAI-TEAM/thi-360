package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phản hồi cố định của yêu cầu quên mật khẩu: giống nhau cho mọi email (AC-06.04)")
public record PasswordForgotResponse(
        @Schema(description = "Thông báo đã bản địa hóa, không cho biết email có tài khoản hay không",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
