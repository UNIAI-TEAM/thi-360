package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Yêu cầu thử giá khuyến mãi trên một bài thi (TM 7.3)")
public record PromotionPricePreviewRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.preview.examId.required}")
        UUID examId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.preview.promotionId.required}")
        UUID promotionId
) {
}
