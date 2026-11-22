package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Yêu cầu gửi lại mã OTP cho phiên đăng ký đang chờ")
public record RegisterResendRequest(
        @Schema(description = "Mã phiên đăng ký chờ xác minh", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.register.registrationId.notNull}")
        UUID registrationId
) {
}
