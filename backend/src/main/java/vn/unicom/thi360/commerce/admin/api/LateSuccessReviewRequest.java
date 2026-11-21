package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.unicom.thi360.commerce.admin.domain.LateSuccessDecision;

/** Yêu cầu xử lý đối soát ca tiền về muộn trên lần thanh toán đã ghi thất bại (BS §14). */
public record LateSuccessReviewRequest(
        @NotNull(message = "{validation.lateSuccess.decision.notNull}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LateSuccessDecision decision,

        @NotBlank(message = "{validation.lateSuccess.reason.notBlank}")
        @Size(max = 1000, message = "{validation.lateSuccess.reason.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,

        @NotBlank(message = "{validation.lateSuccess.evidenceRef.notBlank}")
        @Size(max = 200, message = "{validation.lateSuccess.evidenceRef.size}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String evidenceRef
) {
}
