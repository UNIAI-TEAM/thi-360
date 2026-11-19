package vn.unicom.thi360.exam.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Sale listing rules. {@code minPriceVnd} is {@code THI360_VNPAY_MIN_AMOUNT} (TM E26), shared with commerce. */
@ConfigurationProperties(prefix = "thi360.exam.listing")
public record ExamListingProperties(@DefaultValue("5000") long minPriceVnd) {
    public ExamListingProperties {
        if (minPriceVnd < 1) {
            throw new IllegalArgumentException("minPriceVnd must be positive");
        }
    }
}
