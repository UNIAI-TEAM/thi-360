package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;

@Schema(description = "Thông tin một bài thi thành phần trong chi tiết bộ đề quản trị")
public record BundleItemView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(types = {"string", "null"}) String code,
        @Schema(types = {"string", "null"}) String name,
        @Schema(types = {"string", "null"}) String scope,
        @Schema(types = {"string", "null"}) String partCode,
        @Schema(types = {"string", "null"}) UUID subjectItemId,
        @Schema(types = {"string", "null"}) String subjectCode,
        @Schema(nullable = true) LocalizedName subjectName,
        @Schema(types = {"string", "null"}) String buildMode,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(types = {"integer", "null"}) Integer versionNo,
        @Schema(description = "ON_SALE, DISCONTINUED hoặc UNAVAILABLE", requiredMode = Schema.RequiredMode.REQUIRED)
        String saleState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position) {
}
