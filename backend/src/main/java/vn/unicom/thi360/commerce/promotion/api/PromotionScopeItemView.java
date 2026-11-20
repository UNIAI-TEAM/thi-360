package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Mục phạm vi áp dụng của khuyến mãi")
public record PromotionScopeItemView(
        @Schema(description = "EXAM_KIND | EXAM | BUNDLE", requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(nullable = true) String code,
        @Schema(nullable = true) String name,
        @Schema(nullable = true) String nameEn
) {
}
