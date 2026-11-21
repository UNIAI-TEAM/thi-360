package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryConfiguration;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryProperties;

/**
 * Kiểm thử đơn vị ràng buộc cấu hình {@link QueryDrProperties} và {@link OrderExpiryProperties}
 * ({@code R1-30}, {@code R1-31}, {@code R1-20}).
 */
class QueryDrAndOrderExpiryPropertiesTest {

    @Test
    void appliesDefaultsAndAllowsEqualMinAndMaxAge() {
        QueryDrProperties defaults = new QueryDrProperties(null, null, null, null, null, null, null, null);
        assertThat(defaults.interval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.minAge()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.maxAge()).isEqualTo(Duration.ofHours(24));
        assertThat(defaults.cooldown()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.notFoundGrace()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.batchSize()).isEqualTo(20);
        assertThat(defaults.cycleBudget()).isEqualTo(Duration.ofMinutes(4));
        assertThat(defaults.maxConsecutiveTransportFailures()).isEqualTo(5);

        QueryDrProperties equalWindow = new QueryDrProperties(
                Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofHours(1),
                Duration.ofMinutes(5), Duration.ofMinutes(15), 10, Duration.ofMinutes(2), 3);
        assertThat(equalWindow.minAge()).isEqualTo(equalWindow.maxAge());

        OrderExpiryProperties expiryDefaults = new OrderExpiryProperties(null, null);
        assertThat(expiryDefaults.interval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(expiryDefaults.batchSize()).isEqualTo(20);
    }

    @Test
    void rejectsMinAgeExceedingMaxAgeAndZeroOrNegativeSizes() {
        assertThatThrownBy(() -> new QueryDrProperties(
                Duration.ofMinutes(5), Duration.ofHours(25), Duration.ofHours(24),
                Duration.ofMinutes(5), Duration.ofMinutes(15), 20, Duration.ofMinutes(4), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minAge");

        for (int badBatch : new int[] {0, -1}) {
            assertThatThrownBy(() -> new QueryDrProperties(
                    Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(24),
                    Duration.ofMinutes(5), Duration.ofMinutes(15), badBatch, Duration.ofMinutes(4), 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("batchSize");

            assertThatThrownBy(() -> new OrderExpiryProperties(Duration.ofMinutes(1), badBatch))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("batchSize");
        }

        assertThatThrownBy(() -> new QueryDrProperties(
                Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofMinutes(5), Duration.ofMinutes(15), 20, Duration.ZERO, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycleBudget");

        assertThatThrownBy(() -> new QueryDrProperties(
                Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofMinutes(5), Duration.ofMinutes(15), 20, Duration.ofMinutes(4), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConsecutiveTransportFailures");
    }

    @Test
    void springPropertyBindingRejectsZeroBatchSizeAndInvertedAgeWindow() {
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class)
                .withPropertyValues("thi360.commerce.querydr.batch-size=0")
                .run(ctx -> assertThat(ctx).hasFailed());

        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class)
                .withPropertyValues(
                        "thi360.commerce.querydr.min-age=PT25H",
                        "thi360.commerce.querydr.max-age=PT24H")
                .run(ctx -> assertThat(ctx).hasFailed());

        new ApplicationContextRunner()
                .withUserConfiguration(OrderExpiryConfiguration.class)
                .withPropertyValues("thi360.commerce.order-expiry.batch-size=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
