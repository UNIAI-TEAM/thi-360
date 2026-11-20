package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;

@Schema(description = "Một lần giữ hoặc dùng khuyến mãi / voucher")
public record PromotionRedemptionView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(nullable = true) String orderCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(nullable = true) String username,
        @Schema(nullable = true) String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RedemptionState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
}
