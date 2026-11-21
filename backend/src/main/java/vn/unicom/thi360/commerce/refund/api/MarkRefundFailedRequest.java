package vn.unicom.thi360.commerce.refund.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yêu cầu đánh dấu một giao dịch hoàn {@code UNKNOWN} là {@code FAILED} kèm chứng từ VNPAY (TM 9.4 bước 5). */
public record MarkRefundFailedRequest(
        @NotBlank(message = "{validation.refund.markFailed.reason.notBlank}")
        @Size(max = 1000, message = "{validation.refund.markFailed.reason.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        @NotBlank(message = "{validation.refund.markFailed.evidenceRef.notBlank}")
        @Size(max = 200, message = "{validation.refund.markFailed.evidenceRef.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String evidenceRef
) {
}
