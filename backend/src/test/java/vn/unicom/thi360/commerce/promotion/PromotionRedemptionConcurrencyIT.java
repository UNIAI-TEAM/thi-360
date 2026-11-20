package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.RedemptionView;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/**
 * AC-7, AC-8: Concurrency invariants on {@code PromotionRedemptionService#reserve}.
 */
class PromotionRedemptionConcurrencyIT extends PromotionIntegrationTest {

    @Test
    void lastSlotGoesToOneRequest() throws Exception {
        Promotion voucher = saveVoucher(1, null);
        int threads = 20;
        List<Callable<RedemptionView>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            tasks.add(() -> redemptionService.reserve(voucher.getId(), userId, orderId, 20_000L));
        }

        List<Outcome<RedemptionView>> outcomes = raceAll(tasks);

        List<Outcome<RedemptionView>> successes = outcomes.stream().filter(o -> o.error() == null).toList();
        List<Outcome<RedemptionView>> failures = outcomes.stream().filter(o -> o.error() != null).toList();

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(threads - 1);
        assertThat(failures).allSatisfy(o -> {
            assertThat(o.error()).isInstanceOf(BusinessRuleException.class);
            assertThat(((BusinessRuleException) o.error()).getCode()).isEqualTo("VOUCHER_EXHAUSTED");
        });
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.promotion_redemption WHERE promotion_id = ?",
                Long.class, voucher.getId())).isEqualTo(1L);
    }

    @Test
    void perUserLimitHoldsUnderConcurrency() throws Exception {
        Promotion voucher = saveVoucher(10, 2);
        UUID sameUser = UUID.randomUUID();
        int threads = 15;
        List<Callable<RedemptionView>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            UUID orderId = UUID.randomUUID();
            tasks.add(() -> redemptionService.reserve(voucher.getId(), sameUser, orderId, 20_000L));
        }

        List<Outcome<RedemptionView>> outcomes = raceAll(tasks);

        List<Outcome<RedemptionView>> successes = outcomes.stream().filter(o -> o.error() == null).toList();
        List<Outcome<RedemptionView>> failures = outcomes.stream().filter(o -> o.error() != null).toList();

        assertThat(successes).hasSize(2);
        assertThat(failures).hasSize(threads - 2);
        assertThat(failures).allSatisfy(o -> {
            assertThat(o.error()).isInstanceOf(BusinessRuleException.class);
            assertThat(((BusinessRuleException) o.error()).getCode()).isEqualTo("VOUCHER_USER_LIMIT");
        });
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.promotion_redemption WHERE promotion_id = ? AND user_id = ?",
                Long.class, voucher.getId(), sameUser)).isEqualTo(2L);
    }
}
