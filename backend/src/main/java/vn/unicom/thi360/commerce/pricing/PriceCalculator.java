package vn.unicom.thi360.commerce.pricing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;

/**
 * The one place that turns a list price into the price a student pays (TM 7.2).
 *
 * <p>Provides card/detail display pricing ({@link #priceOf}, {@link #priceOfAll}) as well as full checkout/quote
 * pricing with voucher validation, combination modes, minimum-amount rules, and pending-order exclusion
 * ({@link #calculateQuote}, {@link #previewPromotion}).
 */
public interface PriceCalculator {

    /** The display price of one product, as of now. */
    PriceResult priceOf(PricedProduct product);

    /** Batch display prices of multiple products in a single promotion load (keyed by {@code examId}). */
    Map<UUID, PriceResult> priceOfAll(List<PricedProduct> products);

    /** Full price quote for a product, optional voucher, user, and optional pending order exclusion (TM 7.2). */
    QuoteCalculation calculateQuote(QuoteRequest request);

    /** Admin preview of how one promotion applies to a product (TM 7.3). */
    QuoteCalculation previewPromotion(PricedProduct product, UUID promotionId);

    /**
     * @param examId the exam behind the product, for promotions scoped to an exam
     * @param examKindItemId the catalog item of the exam kind, for promotions scoped to a kind
     * @param listPriceVnd the list price of the serving version
     */
    record PricedProduct(UUID examId, UUID examKindItemId, long listPriceVnd) {
    }

    /**
     * @param listPrice the list price, as given
     * @param price what the student pays after the automatic promotion; never above {@code listPrice}
     * @param promotion the automatic promotion that was applied; null when none
     */
    record PriceResult(long listPrice, long price, AppliedPromotion promotion) {
    }

    /** The automatic promotion shown on a card: its name and when it ends. */
    record AppliedPromotion(String name, Instant endsAt) {
    }

    /** How discounts were applied to a quote (TM 7.2, E6). */
    enum DiscountApplied {
        NONE,
        PROMOTION_ONLY,
        VOUCHER_ONLY,
        COMBINED
    }

    record QuoteRequest(
            PricedProduct product,
            UUID userId,
            String voucherCode,
            UUID excludedPendingOrderId,
            Instant now
    ) {
        public QuoteRequest(PricedProduct product, UUID userId, String voucherCode) {
            this(product, userId, voucherCode, null, null);
        }

        public QuoteRequest(PricedProduct product, UUID userId, String voucherCode, UUID excludedPendingOrderId) {
            this(product, userId, voucherCode, excludedPendingOrderId, null);
        }
    }

    record AppliedAutoPromotion(
            UUID id,
            String name,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            Instant endsAt,
            long discountVnd
    ) {
    }

    record AppliedVoucher(
            UUID id,
            String code,
            String name,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            boolean combinable,
            long discountVnd
    ) {
    }

    /**
     * Full breakdown of a quote calculation. Invariant:
     * {@code listPrice - promotionDiscount - voucherDiscount == finalPrice} and {@code finalPrice >= 0}.
     */
    record QuoteCalculation(
            long listPrice,
            long finalPrice,
            AppliedAutoPromotion promotion,
            long promotionDiscount,
            AppliedVoucher voucher,
            long voucherDiscount,
            DiscountApplied discountApplied,
            boolean voucherNotBetter,
            boolean promotionFloored
    ) {
        public PriceResult toPriceResult() {
            AppliedPromotion badge = promotion == null
                    ? null
                    : new AppliedPromotion(promotion.name(), promotion.endsAt());
            return new PriceResult(listPrice, listPrice - promotionDiscount, badge);
        }
    }
}
