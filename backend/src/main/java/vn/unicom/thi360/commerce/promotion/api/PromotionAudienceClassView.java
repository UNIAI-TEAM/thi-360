package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Lớp học được áp dụng voucher")
public record PromotionAudienceClassView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(nullable = true) String code,
        @Schema(nullable = true) String name
) {
}
