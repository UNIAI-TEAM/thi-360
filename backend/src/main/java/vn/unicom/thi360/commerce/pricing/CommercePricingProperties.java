package vn.unicom.thi360.commerce.pricing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Commerce pricing and voucher rate-limit configuration ({@code thi360.commerce.*}).
 *
 * <p>{@code vnpayMinAmount} reads {@code THI360_VNPAY_MIN_AMOUNT} (default 5.000 VND per TM E26; 0 disables the floor
 * rule).
 */
@ConfigurationProperties(prefix = "thi360.commerce")
public record CommercePricingProperties(
        @DefaultValue("5000") long vnpayMinAmount,
        @DefaultValue VoucherProperties voucher
) {

    public CommercePricingProperties {
        if (vnpayMinAmount < 0) {
            throw new IllegalArgumentException("thi360.commerce.vnpay-min-amount must not be negative");
        }
        if (voucher == null) {
            voucher = new VoucherProperties(10, Duration.ofMinutes(1), 50, Duration.ofHours(24));
        }
    }

    public record VoucherProperties(
            @DefaultValue("10") int quotePerUserMinuteLimit,
            @DefaultValue("PT1M") Duration quotePerUserMinuteWindow,
            @DefaultValue("50") int quotePerUserDayLimit,
            @DefaultValue("PT24H") Duration quotePerUserDayWindow
    ) {
        public VoucherProperties {
            if (quotePerUserMinuteLimit <= 0) {
                throw new IllegalArgumentException(
                        "thi360.commerce.voucher.quote-per-user-minute-limit must be positive");
            }
            if (quotePerUserMinuteWindow == null || quotePerUserMinuteWindow.isZero()
                    || quotePerUserMinuteWindow.isNegative()) {
                throw new IllegalArgumentException(
                        "thi360.commerce.voucher.quote-per-user-minute-window must be a positive duration");
            }
            if (quotePerUserDayLimit <= 0) {
                throw new IllegalArgumentException(
                        "thi360.commerce.voucher.quote-per-user-day-limit must be positive");
            }
            if (quotePerUserDayWindow == null || quotePerUserDayWindow.isZero()
                    || quotePerUserDayWindow.isNegative()) {
                throw new IllegalArgumentException(
                        "thi360.commerce.voucher.quote-per-user-day-window must be a positive duration");
            }
        }
    }
}
