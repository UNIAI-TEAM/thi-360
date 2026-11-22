package vn.unicom.thi360.commerce.pricing;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;

@Schema(description = "Kết quả báo giá sản phẩm (TM 7.2, 10.2)")
public record PriceQuote(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPrice,
        @Schema(nullable = true) PriceQuotePromotion promotion,
        @Schema(nullable = true) PriceQuoteVoucher voucher,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountApplied discountApplied,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean voucherNotBetter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long finalPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean changed
) {
    public static PriceQuote from(StoredProduct product, QuoteCalculation calc, boolean changed) {
        return new PriceQuote(
                product.code(),
                product.name(),
                product.versionNo(),
                product.attemptsGranted(),
                calc.listPrice(),
                PriceQuotePromotion.from(calc.promotion()),
                PriceQuoteVoucher.from(calc.voucher()),
                calc.discountApplied(),
                calc.voucherNotBetter(),
                calc.finalPrice(),
                changed);
    }
}
