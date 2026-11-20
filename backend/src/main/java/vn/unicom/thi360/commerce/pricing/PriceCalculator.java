package vn.unicom.thi360.commerce.pricing;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place that turns a list price into the price a student pays (TM 7.2). CM-1 only needs the display price of a
 * product for the card and the detail page, with no account and no voucher; CM-2 replaces {@link ListPriceCalculator}
 * with the real calculator (promotions, vouchers, minimum amount) and extends this contract for the checkout.
 */
public interface PriceCalculator {

    /** The display price of one product, as of now. */
    PriceResult priceOf(PricedProduct product);

    /**
     * @param examId the exam behind the product, for promotions scoped to an exam
     * @param examKindItemId the catalog item of the exam kind, for promotions scoped to a kind
     * @param listPriceVnd the list price of the serving version
     */
    record PricedProduct(UUID examId, UUID examKindItemId, long listPriceVnd) { }

    /**
     * @param listPrice the list price, as given
     * @param price what the student pays after the automatic promotion; never above {@code listPrice}
     * @param promotion the automatic promotion that was applied; null when none
     */
    record PriceResult(long listPrice, long price, AppliedPromotion promotion) { }

    /** The automatic promotion shown on a card: its name and when it ends. */
    record AppliedPromotion(String name, Instant endsAt) { }
}
