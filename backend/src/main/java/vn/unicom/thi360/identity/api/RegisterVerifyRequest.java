package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Yêu cầu xác nhận mã OTP 6 chữ số và hoàn tất tạo tài khoản học viên")
public record RegisterVerifyRequest(
        @Schema(description = "Mã phiên đăng ký chờ xác minh", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.register.registrationId.notNull}")
        UUID registrationId,

        @Schema(description = "Mã OTP 6 chữ số gửi qua email", example = "123456",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.emailCode.code.notBlank}")
        @Size(max = 16, message = "{validation.emailCode.code.size}")
        String code
) {
    @Override
    public String toString() {
        return "RegisterVerifyRequest[registrationId=" + registrationId
                + ", code=" + (code == null ? "null" : "[REDACTED]") + ']';
    }
}
