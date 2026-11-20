package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu khóa lạc quan cho thao tác dừng/mở lại khuyến mãi")
public record PromotionRevisionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.expectedRevision.required}")
        Long expectedRevision
) {
}
