package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu làm mới phiên trên ứng dụng di động")
public record MobileRefreshRequest(
        @Schema(description = "Refresh token của phiên di động", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.mobile.refreshToken.notBlank}")
        String refreshToken
) {
    @Override
    public String toString() {
        return "MobileRefreshRequest[refreshToken=" + (refreshToken == null ? "null" : "[REDACTED]") + ']';
    }
}
