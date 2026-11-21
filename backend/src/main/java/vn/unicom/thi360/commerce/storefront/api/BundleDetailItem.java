package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Thông tin một bài thi thành phần trong chi tiết bộ đề (TM 16.4, BS 2)")
public record BundleDetailItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "PART hoặc SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED) String scope,
        @Schema(nullable = true) String partCode,
        @Schema(nullable = true) LocalizedName subjectName,
        @Schema(description = "STANDARD hoặc REVIEW", requiredMode = Schema.RequiredMode.REQUIRED) String buildMode,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DetailBenefits benefits,
        @Schema(description = "SCORE, SCORE_AND_ANSWERS hoặc FULL_SOLUTION", nullable = true) String resultView,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) int versionNo) {

    public BundleDetailItem(
            UUID examId,
            String code,
            String name,
            String scope,
            String partCode,
            LocalizedName subjectName,
            String buildMode,
            IntRange questionCount,
            IntRange durationMinutes,
            DecimalRange maxPoints,
            int attemptsGranted,
            DetailBenefits benefits,
            String resultView) {
        this(examId, code, name, scope, partCode, subjectName, buildMode, questionCount, durationMinutes, maxPoints,
                attemptsGranted, benefits, resultView, 1);
    }
}
