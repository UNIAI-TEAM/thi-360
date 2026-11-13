package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả kiểm tra lượt thi đang làm trước khi ngừng hoạt động")
public record DeactivationCheckResponse(
        @Schema(description = "Tài khoản có đang có lượt thi hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean activeAttempt
) {
}
