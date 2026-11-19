package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Mã OTP 6 số gửi qua email")
public record EmailCodeVerifyRequest(
        @Schema(description = "Mã OTP", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.emailCode.code.notBlank}")
        @Size(max = 16, message = "{validation.emailCode.code.size}")
        String code
) {
    @Override
    public String toString() {
        return "EmailCodeVerifyRequest[code=" + (code == null ? "null" : "[REDACTED]") + ']';
    }
}
