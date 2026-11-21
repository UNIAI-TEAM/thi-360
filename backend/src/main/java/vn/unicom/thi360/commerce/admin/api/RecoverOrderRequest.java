package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yêu cầu tiếp tục cấp quyền thủ công cho đơn hàng đã thu tiền (TM 9.5). */
public record RecoverOrderRequest(
        @NotBlank(message = "{validation.order.recover.reason.notBlank}")
        @Size(max = 1000, message = "{validation.order.recover.reason.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason
) {
}
