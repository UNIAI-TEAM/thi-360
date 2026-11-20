package vn.unicom.thi360.commerce.promotion.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** One field delta recorded inside {@link PromotionChangeLog} (BS §10). */
public record PromotionFieldChange(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
        @Schema(nullable = true) Object oldValue,
        @Schema(nullable = true) Object newValue
) {
}
