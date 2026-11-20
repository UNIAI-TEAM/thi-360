package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.RedemptionView;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;

/**
 * AC-16, AC-17, AC-18: State transitions and invariants of {@code PromotionRedemptionService}.
 */
class PromotionRedemptionIT extends PromotionIntegrationTest {

    @Test
    void reserveConfirmReleaseStates() {
        Promotion voucher = saveVoucher(5, 2);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();

        // Reserve -> RESERVED & used_count + 1; idempotent repeat changes nothing.
        RedemptionView reservedA = redemptionService.reserve(voucher.getId(), userA, orderA, "DH001", 20_000L);
        assertThat(reservedA.state()).isEqualTo(RedemptionState.RESERVED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        RedemptionView repeatedReserveA = redemptionService.reserve(voucher.getId(), userA, orderA, "DH001", 20_000L);
        assertThat(repeatedReserveA.id()).isEqualTo(reservedA.id());
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        // Confirm -> CONFIRMED; idempotent repeat keeps CONFIRMED & used_count unchanged.
        RedemptionView confirmedA = redemptionService.confirm(voucher.getId(), orderA);
        assertThat(confirmedA.state()).isEqualTo(RedemptionState.CONFIRMED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        RedemptionView repeatedConfirmA = redemptionService.confirm(voucher.getId(), orderA);
        assertThat(repeatedConfirmA.state()).isEqualTo(RedemptionState.CONFIRMED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        // Separate reserve -> release -> RELEASED & used_count - 1; idempotent repeat keeps count unchanged.
        RedemptionView reservedB = redemptionService.reserve(voucher.getId(), userB, orderB, "DH002", 20_000L);
        assertThat(reservedB.state()).isEqualTo(RedemptionState.RESERVED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(2);

        RedemptionView releasedB = redemptionService.release(voucher.getId(), orderB);
        assertThat(releasedB.state()).isEqualTo(RedemptionState.RELEASED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        RedemptionView repeatedReleaseB = redemptionService.release(voucher.getId(), orderB);
        assertThat(repeatedReleaseB.state()).isEqualTo(RedemptionState.RELEASED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);
    }

    @Test
    void confirmedRedemptionIsNeverReleased() {
        Promotion voucher = saveVoucher(5, null);
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        redemptionService.reserve(voucher.getId(), userId, orderId, 20_000L);
        redemptionService.confirm(voucher.getId(), orderId);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        // E23 / AC-17: release on a CONFIRMED redemption must be a no-op.
        RedemptionView afterRelease = redemptionService.release(voucher.getId(), orderId);
        assertThat(afterRelease.state()).isEqualTo(RedemptionState.CONFIRMED);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);
    }

    @Test
    void lateReclaimMayExceedLimit() {
        Promotion voucher = saveVoucher(1, null);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();

        // Order A reserves and releases, freeing the single slot.
        redemptionService.reserve(voucher.getId(), userA, orderA, 20_000L);
        redemptionService.release(voucher.getId(), orderA);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isZero();

        // Order B takes the single slot.
        redemptionService.reserve(voucher.getId(), userB, orderB, 20_000L);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);
        assertThat(count(promotionRow(voucher.getId()), "over_limit_count")).isZero();

        // Late payment arrives for order A -> reclaim transitions RELEASED -> CONFIRMED and increments over_limit_count.
        RedemptionView reclaimedA = redemptionService.reclaim(voucher.getId(), orderA);
        assertThat(reclaimedA.state()).isEqualTo(RedemptionState.CONFIRMED);
        var row = promotionRow(voucher.getId());
        assertThat(count(row, "used_count")).isEqualTo(2);
        assertThat(count(row, "over_limit_count")).isEqualTo(1);

        // Repeated reclaim is idempotent.
        RedemptionView repeatedReclaim = redemptionService.reclaim(voucher.getId(), orderA);
        assertThat(repeatedReclaim.state()).isEqualTo(RedemptionState.CONFIRMED);
        row = promotionRow(voucher.getId());
        assertThat(count(row, "used_count")).isEqualTo(2);
        assertThat(count(row, "over_limit_count")).isEqualTo(1);
    }
}
