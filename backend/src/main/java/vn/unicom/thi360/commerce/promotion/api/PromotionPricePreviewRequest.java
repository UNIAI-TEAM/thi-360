package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Yêu cầu thử giá khuyến mãi trên một bài thi hoặc bộ đề (TM 7.3, 16.4)")
public record PromotionPricePreviewRequest(
        @Schema(nullable = true)
        UUID examId,

        @Schema(nullable = true)
        UUID bundleId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.preview.promotionId.required}")
        UUID promotionId
) {
    public PromotionPricePreviewRequest(UUID examId, UUID promotionId) {
        this(examId, null, promotionId);
    }
}
