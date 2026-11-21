package vn.unicom.thi360.commerce.order.expiry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình worker hết hạn đơn chờ thanh toán ({@code thi360.commerce.order-expiry.*}).
 */
@ConfigurationProperties(prefix = "thi360.commerce.order-expiry")
public record OrderExpiryProperties(
        @DefaultValue("PT1M") Duration interval,
        @DefaultValue("20") int batchSize
) {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(1);
    private static final int DEFAULT_BATCH_SIZE = 20;

    public OrderExpiryProperties {
        interval = interval == null ? DEFAULT_INTERVAL : requirePositive(interval, "interval");
        batchSize = batchSize == 0 ? DEFAULT_BATCH_SIZE : requirePositiveInt(batchSize, "batchSize");
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("thi360.commerce.order-expiry." + fieldName + " must be positive");
        }
        return duration;
    }

    private static int requirePositiveInt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("thi360.commerce.order-expiry." + fieldName + " must be positive");
        }
        return value;
    }
}
