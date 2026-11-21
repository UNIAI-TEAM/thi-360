package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quyền lợi áp dụng cho từng bài thi trong bộ đề")
public record BundleBenefitsView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean aiExplanation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean strengthWeakness,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean practice,
        @Schema(nullable = true) String note) {
}
