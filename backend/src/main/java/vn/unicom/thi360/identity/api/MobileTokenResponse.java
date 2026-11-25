package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phản hồi token của API xác thực ứng dụng di động")
public record MobileTokenResponse(
        @Schema(description = "Access token dùng cho header Authorization: Bearer",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(description = "Refresh token dùng để làm mới phiên di động",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,

        @Schema(description = "Thời hạn access token tính bằng giây", example = "300",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int accessExpiresIn,

        @Schema(description = "Thời hạn không dùng của phiên di động tính bằng giây", example = "604800",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int refreshExpiresIn,

        @Schema(description = "Tài khoản có bắt buộc đổi mật khẩu trước khi tiếp tục hay không",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword
) {
    @Override
    public String toString() {
        return "MobileTokenResponse["
                + "accessToken=" + (accessToken == null ? "null" : "[REDACTED]")
                + ", refreshToken=" + (refreshToken == null ? "null" : "[REDACTED]")
                + ", accessExpiresIn=" + accessExpiresIn
                + ", refreshExpiresIn=" + refreshExpiresIn
                + ", mustChangePassword=" + mustChangePassword
                + ']';
    }
}
