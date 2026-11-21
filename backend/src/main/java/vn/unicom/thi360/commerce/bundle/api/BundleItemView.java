package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;

@Schema(description = "Thông tin một bài thi thành phần trong chi tiết bộ đề quản trị")
public record BundleItemView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(nullable = true) String code,
        @Schema(nullable = true) String name,
        @Schema(nullable = true) String scope,
        @Schema(nullable = true) String partCode,
        @Schema(nullable = true) UUID subjectItemId,
        @Schema(nullable = true) String subjectCode,
        @Schema(nullable = true) LocalizedName subjectName,
        @Schema(nullable = true) String buildMode,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(nullable = true) Integer versionNo,
        @Schema(description = "ON_SALE, DISCONTINUED hoặc UNAVAILABLE", requiredMode = Schema.RequiredMode.REQUIRED)
        String saleState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position) {
}
