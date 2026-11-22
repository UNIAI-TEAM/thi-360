package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Kết quả khởi tạo hoặc gửi lại mã OTP cho phiên tự đăng ký")
public record RegisterStartResponse(
        @Schema(description = "Mã phiên đăng ký chờ xác minh OTP", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID registrationId,

        @Schema(description = "Địa chỉ email nhận mã OTP xác minh", example = "an@thi360.vn",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(description = "Số giây cần chờ trước khi được phép gửi lại mã OTP", example = "60",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long resendAfterSeconds
) {
}
