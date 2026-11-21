package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.bundle.domain.BundleResultView;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;

@Schema(description = "Chi tiết bộ đề quản trị (TM 16.4)")
public record BundleDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(nullable = true) String summary,
        @Schema(nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examKindItemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindNameVi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examKindNameEn,
        @Schema(nullable = true) Long listPriceVnd,
        @Schema(nullable = true) Integer attemptsPerExam,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BundleBenefitsView benefits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BundleResultView resultView,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BundleStatus status,
        @Schema(nullable = true) SaleState saleState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasUnpublishedChanges,
        @Schema(nullable = true) Integer servingVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BundleItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(nullable = true) UUID createdBy,
        @Schema(nullable = true) String createdByUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(nullable = true) UUID updatedBy,
        @Schema(nullable = true) String updatedByUsername,
        @Schema(nullable = true) Instant discontinuedAt,
        @Schema(nullable = true) UUID discontinuedBy) {

    public BundleDetailResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
