package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quyền lợi sau khi mua (TM 11.3)")
public record DetailBenefits(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean aiExplanation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean strengthWeakness,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean practice,
        @Schema(description = "Ghi chú về quyền lợi", nullable = true) String note) {
}
