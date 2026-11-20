package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.RedemptionView;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.ReserveRequest;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.ids.Ids;

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

    @Test
    void orderBatchOperationsAndRollbackWorkAtomically() {
        Promotion auto = saveAuto(10, PromotionStatus.ACTIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null);
        Promotion voucher = saveVoucher(1, null);

        // Success path: reserveForOrder -> confirmByOrder
        UUID orderConfirmed = UUID.randomUUID();
        List<RedemptionView> reserved = redemptionService.reserveForOrder(
                UUID.randomUUID(), orderConfirmed, "DH-BATCH-1",
                List.of(new ReserveRequest(auto.getId(), 10_000L), new ReserveRequest(voucher.getId(), 20_000L)));
        assertThat(reserved).hasSize(2).allSatisfy(v -> assertThat(v.state()).isEqualTo(RedemptionState.RESERVED));

        List<RedemptionView> confirmed = redemptionService.confirmByOrder(orderConfirmed);
        assertThat(confirmed).hasSize(2).allSatisfy(v -> assertThat(v.state()).isEqualTo(RedemptionState.CONFIRMED));
        assertThat(count(promotionRow(auto.getId()), "used_count")).isEqualTo(1);
        assertThat(count(promotionRow(voucher.getId()), "used_count")).isEqualTo(1);

        // Atomic rollback: second promo is exhausted -> neither redemption nor used_count persists for orderFailed
        UUID orderFailed = UUID.randomUUID();
        assertThatThrownBy(() -> redemptionService.reserveForOrder(
                UUID.randomUUID(), orderFailed, "DH-BATCH-FAIL",
                List.of(new ReserveRequest(auto.getId(), 10_000L), new ReserveRequest(voucher.getId(), 20_000L))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_EXHAUSTED");
        assertThat(count(promotionRow(auto.getId()), "used_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.promotion_redemption WHERE order_id = ?",
                Long.class, orderFailed)).isZero();

        // releaseByOrder + reclaimByOrder within limit keeps over_limit_count == 0
        Promotion secondVoucher = saveVoucher(5, null);
        UUID orderReleaseReclaim = UUID.randomUUID();
        redemptionService.reserveForOrder(
                UUID.randomUUID(), orderReleaseReclaim, "DH-BATCH-REL",
                List.of(new ReserveRequest(auto.getId(), 10_000L), new ReserveRequest(secondVoucher.getId(), 15_000L)));
        List<RedemptionView> released = redemptionService.releaseByOrder(orderReleaseReclaim);
        assertThat(released).hasSize(2).allSatisfy(v -> assertThat(v.state()).isEqualTo(RedemptionState.RELEASED));
        assertThat(count(promotionRow(auto.getId()), "used_count")).isEqualTo(1);
        assertThat(count(promotionRow(secondVoucher.getId()), "used_count")).isZero();

        List<RedemptionView> reclaimed = redemptionService.reclaimByOrder(orderReleaseReclaim);
        assertThat(reclaimed).hasSize(2).allSatisfy(v -> assertThat(v.state()).isEqualTo(RedemptionState.CONFIRMED));
        assertThat(count(promotionRow(auto.getId()), "over_limit_count")).isZero();
        assertThat(count(promotionRow(secondVoucher.getId()), "over_limit_count")).isZero();
    }

    @Test
    void stateAndEligibilityGuardsRejectInvalidTransitions() {
        Instant now = Instant.now();
        Promotion stopped = saveAuto(5, PromotionStatus.STOPPED,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null);
        Promotion expired = saveAuto(5, PromotionStatus.ACTIVE,
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.MINUTES), null);
        Promotion upcoming = saveAuto(5, PromotionStatus.ACTIVE,
                now.plus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null);
        Promotion deleted = saveAuto(5, PromotionStatus.ACTIVE,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), now.minusSeconds(10));

        assertThatThrownBy(() -> redemptionService.reserve(stopped.getId(), UUID.randomUUID(), UUID.randomUUID(), 5_000L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_INVALID");
        assertThatThrownBy(() -> redemptionService.reserve(expired.getId(), UUID.randomUUID(), UUID.randomUUID(), 5_000L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_EXPIRED");
        assertThatThrownBy(() -> redemptionService.reserve(upcoming.getId(), UUID.randomUUID(), UUID.randomUUID(), 5_000L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_INVALID");
        assertThatThrownBy(() -> redemptionService.reserve(deleted.getId(), UUID.randomUUID(), UUID.randomUUID(), 5_000L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_INVALID");

        // Released redemption cannot be re-reserved or confirmed (409 CONFLICT); within-limit reclaim keeps over_limit=0
        Promotion voucher = saveVoucher(2, null);
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        redemptionService.reserve(voucher.getId(), userId, orderId, 10_000L);
        redemptionService.release(voucher.getId(), orderId);

        assertThatThrownBy(() -> redemptionService.reserve(voucher.getId(), userId, orderId, 10_000L))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> redemptionService.confirm(voucher.getId(), orderId))
                .isInstanceOf(ConflictException.class);

        RedemptionView reclaimed = redemptionService.reclaim(voucher.getId(), orderId);
        assertThat(reclaimed.state()).isEqualTo(RedemptionState.CONFIRMED);
        var row = promotionRow(voucher.getId());
        assertThat(count(row, "used_count")).isEqualTo(1);
        assertThat(count(row, "over_limit_count")).isZero();
    }

    private Promotion saveAuto(
            Integer totalLimit, PromotionStatus status, Instant startsAt, Instant endsAt, Instant deletedAt) {
        Instant now = Instant.now();
        UUID id = trackPromotion(Ids.newId());
        Promotion auto = Promotion.auto(
                id, "Auto " + id, null, DiscountType.AMOUNT, 10_000L, null,
                startsAt, endsAt, totalLimit, PromotionScope.ALL, status, now, null, null);
        if (deletedAt != null) {
            auto.softDelete(deletedAt, null, null);
        }
        return promotionRepository.saveAndFlush(auto);
    }
}
