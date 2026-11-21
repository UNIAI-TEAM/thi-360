package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;

@Schema(description = "Bài thi đang bán đủ điều kiện đưa vào bộ đề (TM 16.4, BS 2)")
public record BundleCandidateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "PART hoặc SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED) String scope,
        @Schema(nullable = true) String partCode,
        @Schema(nullable = true) UUID subjectItemId,
        @Schema(nullable = true) String subjectCode,
        @Schema(nullable = true) LocalizedName subjectName,
        @Schema(description = "STANDARD hoặc REVIEW", requiredMode = Schema.RequiredMode.REQUIRED) String buildMode,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
        @Schema(nullable = true) Long listPriceVnd) {
}
