package vn.unicom.thi360.commerce.order.application;

import java.util.Objects;
import vn.unicom.thi360.commerce.pricing.PriceQuote;

/**
 * Ném ra khi phiên bản hoặc số tiền phải trả tính lại khác với báo giá học viên vừa xác nhận (TM 8.3, BR-05).
 */
public class OrderPriceChangedException extends RuntimeException {

    private final PriceQuote quote;

    public OrderPriceChangedException(PriceQuote quote) {
        super("PRICE_CHANGED");
        this.quote = Objects.requireNonNull(quote, "quote must not be null");
    }

    public PriceQuote quote() {
        return quote;
    }
}
