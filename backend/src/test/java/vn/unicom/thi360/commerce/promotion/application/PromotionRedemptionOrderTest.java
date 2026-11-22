package vn.unicom.thi360.commerce.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link PromotionRedemptionService#compareUuidUnsigned} matches PostgreSQL unsigned {@code uuid} byte order
 * (R1-1..R1-3).
 */
class PromotionRedemptionOrderTest {

    @Test
    void compareUuidUnsignedMatchesPostgresByteOrder() {
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID midLow = UUID.fromString("7fffffff-ffff-4fff-8000-000000000001");
        UUID midHigh = UUID.fromString("80000000-0000-4000-8000-000000000001");
        UUID high = UUID.fromString("90000000-0000-4000-8000-000000000001");
        UUID lsbLow = UUID.fromString("10000000-0000-4000-0000-000000000001");
        UUID lsbHigh = UUID.fromString("10000000-0000-4000-9000-000000000001");

        // Signed Java UUID.compareTo puts high/midHigh before low/midLow; unsigned comparison must match PostgreSQL.
        assertThat(high.compareTo(low)).isNegative();
        assertThat(PromotionRedemptionService.compareUuidUnsigned(low, high)).isNegative();
        assertThat(PromotionRedemptionService.compareUuidUnsigned(high, low)).isPositive();
        assertThat(PromotionRedemptionService.compareUuidUnsigned(midLow, midHigh)).isNegative();
        assertThat(PromotionRedemptionService.compareUuidUnsigned(lsbLow, lsbHigh)).isNegative();
        assertThat(PromotionRedemptionService.compareUuidUnsigned(low, low)).isZero();

        List<UUID> sorted = new ArrayList<>(List.of(high, lsbHigh, midHigh, low, lsbLow, midLow));
        sorted.sort(PromotionRedemptionService::compareUuidUnsigned);
        assertThat(sorted).containsExactly(low, lsbLow, lsbHigh, midLow, midHigh, high);
    }
}
