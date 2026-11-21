package vn.unicom.thi360.commerce.payment.vnpay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình worker tra cứu giao dịch VNPAY {@code querydr} ({@code thi360.commerce.querydr.*}).
 */
@ConfigurationProperties(prefix = "thi360.commerce.querydr")
public record QueryDrProperties(
        @DefaultValue("PT5M") Duration interval,
        @DefaultValue("PT15M") Duration minAge,
        @DefaultValue("PT24H") Duration maxAge,
        @DefaultValue("PT5M") Duration cooldown,
        @DefaultValue("PT15M") Duration notFoundGrace,
        @DefaultValue("20") int batchSize
) {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_MIN_AGE = Duration.ofMinutes(15);
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(24);
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration DEFAULT_NOT_FOUND_GRACE = Duration.ofMinutes(15);
    private static final int DEFAULT_BATCH_SIZE = 20;

    public QueryDrProperties {
        interval = interval == null ? DEFAULT_INTERVAL : requirePositive(interval, "interval");
        minAge = minAge == null ? DEFAULT_MIN_AGE : requirePositive(minAge, "minAge");
        maxAge = maxAge == null ? DEFAULT_MAX_AGE : requirePositive(maxAge, "maxAge");
        cooldown = cooldown == null ? DEFAULT_COOLDOWN : requirePositive(cooldown, "cooldown");
        notFoundGrace = notFoundGrace == null
                ? DEFAULT_NOT_FOUND_GRACE
                : requirePositive(notFoundGrace, "notFoundGrace");
        batchSize = batchSize == 0 ? DEFAULT_BATCH_SIZE : requirePositiveInt(batchSize, "batchSize");
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("thi360.commerce.querydr." + fieldName + " must be positive");
        }
        return duration;
    }

    private static int requirePositiveInt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("thi360.commerce.querydr." + fieldName + " must be positive");
        }
        return value;
    }
}
