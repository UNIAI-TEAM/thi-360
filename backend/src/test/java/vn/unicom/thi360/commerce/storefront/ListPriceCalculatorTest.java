package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.pricing.ListPriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PriceResult;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;

/**
 * AC-21 (CM-1). This pins TEMPORARY behaviour (plan B1): until CM-2 adds promotions, the price of a product is its list
 * price and there is never a promotion. CM-2 deletes this class together with {@link ListPriceCalculator}.
 */
@DisplayName("ListPriceCalculator: temporary calculator until CM-2, price equals list price, no promotion")
class ListPriceCalculatorTest {

    private final PriceCalculator calculator = new ListPriceCalculator();

    @Test
    void priceEqualsListPriceAndThereIsNoPromotion() {
        PriceResult result = calculator.priceOf(new PricedProduct(UUID.randomUUID(), UUID.randomUUID(), 249_000L));

        assertThat(result.listPrice()).isEqualTo(249_000L);
        assertThat(result.price()).isEqualTo(249_000L);
        assertThat(result.promotion()).isNull();
    }

    @Test
    void freeProductStaysFree() {
        PriceResult result = calculator.priceOf(new PricedProduct(UUID.randomUUID(), null, 0L));

        assertThat(result.listPrice()).isZero();
        assertThat(result.price()).isZero();
        assertThat(result.promotion()).isNull();
    }
}
