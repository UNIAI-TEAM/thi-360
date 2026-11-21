package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;

@Schema(description = "Tóm tắt bộ đề trong danh sách quản trị (TM 16.4)")
public record BundleSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(nullable = true) String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examKindItemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindNameVi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindNameEn,
        @Schema(nullable = true) Long listPriceVnd,
        @Schema(nullable = true) Integer attemptsPerExam,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BundleStatus status,
        @Schema(nullable = true) SaleState saleState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasUnpublishedChanges,
        @Schema(nullable = true) Integer servingVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int itemCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
}
