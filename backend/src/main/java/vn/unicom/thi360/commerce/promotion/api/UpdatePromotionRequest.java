package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;

@Schema(description = "Yêu cầu cập nhật khuyến mãi hoặc voucher (TM 7.3)")
public record UpdatePromotionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.expectedRevision.required}")
        Long expectedRevision,

        @Schema(nullable = true) PromotionKind kind,
        @Schema(nullable = true) String name,
        @Schema(nullable = true) String description,
        @Schema(nullable = true) Boolean clearDescription,
        @Schema(nullable = true) String code,
        @Schema(nullable = true) DiscountType discountType,
        @Schema(nullable = true) Long discountValue,
        @Schema(nullable = true) Long maxDiscountVnd,
        @Schema(nullable = true) Boolean clearMaxDiscountVnd,
        @Schema(nullable = true) Instant startsAt,
        @Schema(nullable = true) Instant endsAt,
        @Schema(nullable = true) Integer totalLimit,
        @Schema(nullable = true) Boolean clearTotalLimit,
        @Schema(nullable = true) Integer perUserLimit,
        @Schema(nullable = true) Boolean clearPerUserLimit,
        @Schema(nullable = true) Boolean combinable,
        @Schema(nullable = true) PromotionScope scope,
        @Schema(nullable = true) List<UUID> examKindIds,
        @Schema(nullable = true) List<UUID> examIds,
        @Schema(nullable = true) PromotionAudienceType audience,
        @Schema(nullable = true) List<UUID> audienceClassIds,
        @Schema(nullable = true) List<UUID> audienceUserIds,
        @Schema(nullable = true) PromotionStatus status
) {
}
