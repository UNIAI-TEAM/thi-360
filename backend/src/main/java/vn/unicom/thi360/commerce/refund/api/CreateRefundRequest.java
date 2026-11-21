package vn.unicom.thi360.commerce.refund.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.unicom.thi360.commerce.refund.domain.RefundKind;

/** Yêu cầu tạo giao dịch hoàn tiền qua API VNPAY hoặc ghi nhận thủ công (TM 9.4). */
public record CreateRefundRequest(
        @NotNull(message = "{validation.refund.amountVnd.notNull}")
        @Min(value = 1, message = "{validation.refund.amountVnd.min}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long amountVnd,
        RefundKind kind,
        @NotBlank(message = "{validation.refund.reason.notBlank}")
        @Size(max = 1000, message = "{validation.refund.reason.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        Boolean verifiedSystemError,
        @Size(max = 200)
        String externalRef
) {
}
