package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
            UUID orderId = stubOrder(userId);
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
            UUID orderId = stubOrder(sameUser);
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

    @Test
    void unsignedLockOrderPreventsDeadlocksAcrossStraddlingUuids() throws Exception {
        String hex12 = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UUID lowId = trackPromotion(UUID.fromString("00000000-0000-4000-8000-" + hex12));
        UUID highId = trackPromotion(UUID.fromString("90000000-0000-4000-8000-" + hex12));
        // Confirm signed Java compareTo differs from PostgreSQL unsigned ORDER BY ASC
        assertThat(highId.compareTo(lowId)).isNegative();

        Instant now = Instant.now();
        promotionRepository.saveAndFlush(Promotion.auto(
                lowId, "Low Auto " + hex12, null, DiscountType.AMOUNT, 10_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.ALL, PromotionStatus.ACTIVE, now, null, null));
        promotionRepository.saveAndFlush(Promotion.voucher(
                highId, "High Voucher " + hex12, null, "HGH" + hex12.substring(0, 6).toUpperCase(),
                DiscountType.AMOUNT, 15_000L, null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                null, null, true, PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE,
                now, null, null));

        List<UUID> pgOrder = jdbc.queryForList(
                "SELECT id FROM commerce.promotion WHERE id IN (?, ?) ORDER BY id ASC",
                UUID.class, highId, lowId);
        assertThat(pgOrder).containsExactly(lowId, highId);

        // reserveForOrder given [highId, lowId] must lock in PostgreSQL unsigned order [lowId, highId]
        UUID verifyUserId = UUID.randomUUID();
        UUID verifyOrderId = stubOrder(verifyUserId, "DH-ORD");
        List<RedemptionView> reservedViews = redemptionService.reserveForOrder(
                verifyUserId, verifyOrderId, "DH-ORD",
                List.of(new ReserveRequest(highId, 15_000L), new ReserveRequest(lowId, 10_000L)));
        assertThat(reservedViews).extracting(RedemptionView::promotionId).containsExactly(lowId, highId);

        // Pre-reserve orders that will concurrently run releaseByOrder / confirmByOrder against new reserveForOrder calls
        int pairedOrders = 6;
        List<UUID> toRelease = new ArrayList<>(pairedOrders);
        List<UUID> toConfirm = new ArrayList<>(pairedOrders);
        for (int i = 0; i < pairedOrders; i++) {
            UUID relUser = UUID.randomUUID();
            UUID cnfUser = UUID.randomUUID();
            UUID relOrder = stubOrder(relUser, "DH-REL-" + i);
            UUID cnfOrder = stubOrder(cnfUser, "DH-CNF-" + i);
            redemptionService.reserveForOrder(relUser, relOrder, "DH-REL-" + i,
                    List.of(new ReserveRequest(highId, 15_000L), new ReserveRequest(lowId, 10_000L)));
            redemptionService.reserveForOrder(cnfUser, cnfOrder, "DH-CNF-" + i,
                    List.of(new ReserveRequest(highId, 15_000L), new ReserveRequest(lowId, 10_000L)));
            toRelease.add(relOrder);
            toConfirm.add(cnfOrder);
        }

        int newReservations = 8;
        List<Callable<List<RedemptionView>>> tasks = new ArrayList<>();
        for (int i = 0; i < newReservations; i++) {
            UUID uid = UUID.randomUUID();
            String code = "DH-NEW-" + i;
            UUID oid = stubOrder(uid, code);
            tasks.add(() -> redemptionService.reserveForOrder(
                    uid, oid, code,
                    List.of(new ReserveRequest(highId, 15_000L), new ReserveRequest(lowId, 10_000L))));
        }
        for (UUID relOrder : toRelease) {
            tasks.add(() -> redemptionService.releaseByOrder(relOrder));
        }
        for (UUID cnfOrder : toConfirm) {
            tasks.add(() -> redemptionService.confirmByOrder(cnfOrder));
        }

        List<Outcome<List<RedemptionView>>> outcomes = raceAll(tasks);
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());

        int expectedUsed = 1 + pairedOrders + newReservations;
        assertThat(count(promotionRow(lowId), "used_count")).isEqualTo(expectedUsed);
        assertThat(count(promotionRow(highId), "used_count")).isEqualTo(expectedUsed);
        for (UUID relOrder : toRelease) {
            List<String> states = jdbc.queryForList(
                    "SELECT state FROM commerce.promotion_redemption WHERE order_id = ?",
                    String.class, relOrder);
            assertThat(states).containsExactly(RedemptionState.RELEASED.name(), RedemptionState.RELEASED.name());
        }
        for (UUID cnfOrder : toConfirm) {
            List<String> states = jdbc.queryForList(
                    "SELECT state FROM commerce.promotion_redemption WHERE order_id = ?",
                    String.class, cnfOrder);
            assertThat(states).containsExactly(RedemptionState.CONFIRMED.name(), RedemptionState.CONFIRMED.name());
        }
    }
}
