package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.PromotionTimeState;
import vn.unicom.thi360.shared.api.PageResponse;

@Schema(description = "Chi tiết khuyến mãi / voucher kèm lượt dùng và lịch sử thay đổi (TM 7.3, BS §10)")
public record PromotionDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PromotionKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(nullable = true) String description,
        @Schema(nullable = true) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountType discountType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountValue,
        @Schema(nullable = true) Long maxDiscountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(nullable = true) Integer totalLimit,
        @Schema(nullable = true) Integer perUserLimit,
        @Schema(nullable = true) Boolean combinable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PromotionScope scope,
        @Schema(nullable = true) PromotionAudienceType audience,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PromotionStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PromotionTimeState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int usedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int overLimitCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CapWarning> capWarnings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PromotionScopeItemView> scopeItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PromotionAudienceClassView> audienceClasses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PromotionAudienceUserView> audienceUsers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long confirmedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long reservedCount,
        @Schema(nullable = true) UUID createdBy,
        @Schema(nullable = true) String createdByUsername,
        @Schema(nullable = true) UUID updatedBy,
        @Schema(nullable = true) String updatedByUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalDiscountPaid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PageResponse<PromotionRedemptionView> redemptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PromotionChangeLogView> history
) {
}
