package vn.unicom.thi360.commerce.pricing;

import org.springframework.stereotype.Component;

/**
 * Temporary calculator of CM-1 (plan B1): there are no promotions yet, so the price is the list price. CM-2 deletes this
 * class together with {@code ListPriceCalculatorTest} when it adds the real {@link PriceCalculator}.
 */
@Component
public class ListPriceCalculator implements PriceCalculator {

    @Override
    public PriceResult priceOf(PricedProduct product) {
        return new PriceResult(product.listPriceVnd(), product.listPriceVnd(), null);
    }
}
