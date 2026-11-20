package vn.unicom.thi360.commerce.pricing;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;

@Schema(description = "Voucher được áp dụng trong báo giá")
public record PriceQuoteVoucher(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountType discountType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountValue,
        @Schema(nullable = true) Long maxDiscountVnd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean combinable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountVnd
) {
    public static PriceQuoteVoucher from(PriceCalculator.AppliedVoucher src) {
        return src == null ? null : new PriceQuoteVoucher(
                src.id(),
                src.code(),
                src.name(),
                src.discountType(),
                src.discountValue(),
                src.maxDiscountVnd(),
                src.combinable(),
                src.discountVnd());
    }
}
