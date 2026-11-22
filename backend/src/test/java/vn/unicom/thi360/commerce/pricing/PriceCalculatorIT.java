package vn.unicom.thi360.commerce.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteRequest;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-15: Verifies {@link PriceCalculator#calculateQuote} excludes the learner's own pending order reservation from both
 * {@code total_limit} and {@code per_user_limit} (for both vouchers and automatic promotions).
 */
class PriceCalculatorIT extends AbstractIntegrationTest {

    @Autowired
    private PriceCalculator priceCalculator;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionAudienceRepository audienceRepository;

    @Autowired
    private PromotionRedemptionService redemptionService;

    @Autowired
    private JdbcTemplate jdbc;

    private final Set<UUID> createdPromotionIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> createdOrderIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanup() {
        for (UUID id : createdPromotionIds) {
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", id);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", id);
        }
        createdPromotionIds.clear();
        for (UUID orderId : createdOrderIds) {
            CommerceTestFixtures.deleteStubOrder(jdbc, orderId);
        }
        createdOrderIds.clear();
    }

    @Test
    void ownPendingReservationIsExcluded() {
        Instant now = Instant.now();
        UUID examId = UUID.randomUUID();
        UUID kindId = UUID.randomUUID();
        PricedProduct product = new PricedProduct(examId, kindId, 200_000L);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID orderA = stubOrder(userA, examId, "DH-A-" + shortToken());

        // 1. Voucher with total_limit = 1, per_user_limit = 1, reserved by orderA of userA
        UUID voucherId = track(Ids.newId());
        String voucherCode = "OWN" + voucherId.toString().substring(0, 6).toUpperCase();
        promotionRepository.saveAndFlush(Promotion.voucher(
                voucherId, "Own Pending Voucher", null, voucherCode, DiscountType.AMOUNT, 40_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                1, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE,
                now, null, null));
        redemptionService.reserve(voucherId, userA, orderA, "DH-A", 40_000L);

        // Quoting for userA with excludedPendingOrderId = orderA succeeds and applies voucher
        QuoteCalculation ownQuote = priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, voucherCode, orderA, now));
        assertThat(ownQuote.discountApplied()).isEqualTo(DiscountApplied.VOUCHER_ONLY);
        assertThat(ownQuote.finalPrice()).isEqualTo(160_000L);
        assertThat(ownQuote.voucher()).isNotNull();
        assertThat(ownQuote.voucher().id()).isEqualTo(voucherId);

        // Quoting for userA without excluding orderA or for userB fails with VOUCHER_EXHAUSTED
        assertThatThrownBy(() -> priceCalculator.calculateQuote(new QuoteRequest(product, userA, voucherCode, null, now)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_EXHAUSTED");
        assertThatThrownBy(() -> priceCalculator.calculateQuote(new QuoteRequest(product, userB, voucherCode, null, now)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_EXHAUSTED");

        // 2. Voucher with total_limit = 10, per_user_limit = 1, reserved by orderA of userA
        UUID perUserVoucherId = track(Ids.newId());
        String perUserCode = "USR" + perUserVoucherId.toString().substring(0, 6).toUpperCase();
        promotionRepository.saveAndFlush(Promotion.voucher(
                perUserVoucherId, "Per User Voucher", null, perUserCode, DiscountType.AMOUNT, 30_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                10, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE,
                now, null, null));
        redemptionService.reserve(perUserVoucherId, userA, orderA, "DH-A", 30_000L);

        QuoteCalculation excludedUserLimit = priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, perUserCode, orderA, now));
        assertThat(excludedUserLimit.finalPrice()).isEqualTo(170_000L);

        assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, perUserCode, UUID.randomUUID(), now)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_USER_LIMIT");

        // 3. Auto-promotion with total_limit = 1, reserved by orderA of userA
        UUID autoId = track(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.auto(
                autoId, "Limited Auto", null, DiscountType.AMOUNT, 50_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                1, PromotionScope.ALL, PromotionStatus.ACTIVE, now, null, null));
        redemptionService.reserve(autoId, userA, orderA, "DH-A", 50_000L);

        QuoteCalculation autoWithOwnPending = priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, null, orderA, now));
        assertThat(autoWithOwnPending.promotion()).isNotNull();
        assertThat(autoWithOwnPending.promotion().id()).isEqualTo(autoId);
        assertThat(autoWithOwnPending.finalPrice()).isEqualTo(150_000L);

        QuoteCalculation autoWithoutExcluded = priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, null, null, now));
        assertThat(autoWithoutExcluded.promotion()).isNull();
        assertThat(autoWithoutExcluded.finalPrice()).isEqualTo(200_000L);

        QuoteCalculation autoForOtherUser = priceCalculator.calculateQuote(
                new QuoteRequest(product, userB, null, null, now));
        assertThat(autoForOtherUser.promotion()).isNull();
        assertThat(autoForOtherUser.finalPrice()).isEqualTo(200_000L);

        // 4. Passing userA's pending orderA on a quote for userB (or anonymous user) does NOT unlock voucher or auto
        assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(product, userB, voucherCode, orderA, now)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_EXHAUSTED");

        QuoteCalculation autoOtherUserWithOrderA = priceCalculator.calculateQuote(
                new QuoteRequest(product, userB, null, orderA, now));
        assertThat(autoOtherUserWithOrderA.promotion()).isNull();
        assertThat(autoOtherUserWithOrderA.finalPrice()).isEqualTo(200_000L);

        QuoteCalculation autoAnonymousWithOrderA = priceCalculator.calculateQuote(
                new QuoteRequest(product, null, null, orderA, now));
        assertThat(autoAnonymousWithOrderA.promotion()).isNull();
        assertThat(autoAnonymousWithOrderA.finalPrice()).isEqualTo(200_000L);
    }

    @Test
    void overLimitAutoPromotionIsNotQuotedForPendingOrder() {
        Instant now = Instant.now();
        PricedProduct product = new PricedProduct(UUID.randomUUID(), UUID.randomUUID(), 200_000L);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID orderA = stubOrder(userA, product.examId(), "DH-A-" + shortToken());
        UUID orderB = stubOrder(userB, product.examId(), "DH-B-" + shortToken());

        UUID autoId = track(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.auto(
                autoId, "Over Limit Auto", null, DiscountType.AMOUNT, 50_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                1, PromotionScope.ALL, PromotionStatus.ACTIVE, now, null, null));

        // orderB reserves and releases, then orderA takes the slot, then late reclaim on orderB pushes used_count = 2
        redemptionService.reserve(autoId, userB, orderB, "DH-B", 50_000L);
        redemptionService.release(autoId, orderB);
        redemptionService.reserve(autoId, userA, orderA, "DH-A", 50_000L);
        redemptionService.reclaim(autoId, orderB);

        // Even when quoting for orderA, used_count (2) exceeds total_limit (1) so auto promo must NOT apply
        QuoteCalculation quote = priceCalculator.calculateQuote(
                new QuoteRequest(product, userA, null, orderA, now));
        assertThat(quote.promotion()).isNull();
        assertThat(quote.promotionDiscount()).isZero();
        assertThat(quote.finalPrice()).isEqualTo(200_000L);
    }

    @Test
    void selectedClassVoucherForUnknownUserThrowsNotEligibleWithoutRollbackPoisoning() {
        Instant now = Instant.now();
        PricedProduct product = new PricedProduct(UUID.randomUUID(), UUID.randomUUID(), 200_000L);
        UUID voucherId = track(Ids.newId());
        String code = "CLS" + voucherId.toString().substring(0, 6).toUpperCase();
        promotionRepository.saveAndFlush(Promotion.voucher(
                voucherId, "Class Only Voucher", null, code, DiscountType.AMOUNT, 25_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                null, null, false, PromotionScope.ALL, PromotionAudienceType.SELECTED, PromotionStatus.ACTIVE,
                now, null, null));
        audienceRepository.saveAndFlush(PromotionAudience.forClass(Ids.newId(), voucherId, UUID.randomUUID()));

        UUID unknownUser = UUID.randomUUID();
        assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(product, unknownUser, code, null, now)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("VOUCHER_NOT_ELIGIBLE");
    }

    private UUID track(UUID id) {
        createdPromotionIds.add(id);
        return id;
    }

    private UUID stubOrder(UUID userId, UUID examId, String orderCode) {
        UUID orderId = Ids.newId();
        CommerceTestFixtures.insertMinimalStubOrder(jdbc, orderId, userId, examId, orderCode);
        createdOrderIds.add(orderId);
        return orderId;
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
