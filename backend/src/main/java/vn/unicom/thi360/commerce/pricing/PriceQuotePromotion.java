package vn.unicom.thi360.commerce.pricing;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;

@Schema(description = "Khuyến mãi tự động được áp dụng trong báo giá")
public record PriceQuotePromotion(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountType discountType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountValue,
        @Schema(nullable = true) Long maxDiscountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountVnd
) {
    public static PriceQuotePromotion from(PriceCalculator.AppliedAutoPromotion src) {
        return src == null ? null : new PriceQuotePromotion(
                src.id(),
                src.name(),
                src.discountType(),
                src.discountValue(),
                src.maxDiscountVnd(),
                src.endsAt(),
                src.discountVnd());
    }
}
