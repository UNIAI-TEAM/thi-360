package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu đăng xuất phiên trên ứng dụng di động")
public record MobileLogoutRequest(
        @Schema(description = "Refresh token của phiên di động cần thu hồi", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.mobile.refreshToken.notBlank}")
        String refreshToken
) {
    @Override
    public String toString() {
        return "MobileLogoutRequest[refreshToken=" + (refreshToken == null ? "null" : "[REDACTED]") + ']';
    }
}
