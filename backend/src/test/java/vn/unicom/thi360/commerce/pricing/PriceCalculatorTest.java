package vn.unicom.thi360.commerce.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PricingEngine.AutoCandidate;
import vn.unicom.thi360.commerce.pricing.PricingEngine.VoucherCandidate;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;

/**
 * AC-1 to AC-6: Pure unit tests of {@link PricingEngine} covering percentage/amount math, floor rounding,
 * auto-promotion tie-breaking, voucher combination modes, unused voucher omission, and E26 minimum amount rules.
 */
class PriceCalculatorTest {

    private static final Instant T1 = Instant.parse("2026-11-21T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-22T00:00:00Z");

    private static AutoCandidate autoPercent(String name, long pct, Long maxCap, Instant endsAt) {
        return new AutoCandidate(UUID.randomUUID(), name, DiscountType.PERCENT, pct, maxCap, endsAt);
    }

    private static AutoCandidate autoAmount(String name, long amount, Instant endsAt) {
        return new AutoCandidate(UUID.randomUUID(), name, DiscountType.AMOUNT, amount, null, endsAt);
    }

    private static VoucherCandidate voucherPercent(String code, long pct, Long maxCap, boolean combinable) {
        return new VoucherCandidate(UUID.randomUUID(), code, "Voucher " + code, DiscountType.PERCENT, pct, maxCap,
                combinable);
    }

    private static VoucherCandidate voucherAmount(String code, long amount, boolean combinable) {
        return new VoucherCandidate(UUID.randomUUID(), code, "Voucher " + code, DiscountType.AMOUNT, amount, null,
                combinable);
    }

    @Test
    void discountTable() {
        // % without cap
        QuoteCalculation pctNoCap = PricingEngine.calculate(
                200_000L, List.of(autoPercent("20%", 20, null, T1)), null, 5_000L);
        assertThat(pctNoCap.promotionDiscount()).isEqualTo(40_000L);
        assertThat(pctNoCap.finalPrice()).isEqualTo(160_000L);

        // % capped by max_discount_vnd
        QuoteCalculation pctCapped = PricingEngine.calculate(
                200_000L, List.of(autoPercent("50% cap 30k", 50, 30_000L, T1)), null, 5_000L);
        assertThat(pctCapped.promotionDiscount()).isEqualTo(30_000L);
        assertThat(pctCapped.finalPrice()).isEqualTo(170_000L);

        // Fixed amount
        QuoteCalculation fixedAmount = PricingEngine.calculate(
                200_000L, List.of(autoAmount("-45k", 45_000L, T1)), null, 5_000L);
        assertThat(fixedAmount.promotionDiscount()).isEqualTo(45_000L);
        assertThat(fixedAmount.finalPrice()).isEqualTo(155_000L);

        // Floor rounding to VND: floor(100_001 * 33 / 100) = floor(33000.33) = 33_000
        QuoteCalculation flooredVnd = PricingEngine.calculate(
                100_001L, List.of(autoPercent("33%", 33, null, T1)), null, 5_000L);
        assertThat(flooredVnd.promotionDiscount()).isEqualTo(33_000L);
        assertThat(flooredVnd.finalPrice()).isEqualTo(67_001L);

        // 1% boundary
        QuoteCalculation onePercent = PricingEngine.calculate(
                100_099L, List.of(autoPercent("1%", 1, null, T1)), null, 0L);
        assertThat(onePercent.promotionDiscount()).isEqualTo(1_000L);
        assertThat(onePercent.finalPrice()).isEqualTo(99_099L);

        // 100% boundary & fixed amount larger than list price: never negative
        QuoteCalculation fullPercent = PricingEngine.calculate(
                120_000L, List.of(autoPercent("100%", 100, null, T1)), null, 0L);
        assertThat(fullPercent.promotionDiscount()).isEqualTo(120_000L);
        assertThat(fullPercent.finalPrice()).isZero();

        QuoteCalculation oversizedAmount = PricingEngine.calculate(
                120_000L, List.of(autoAmount("-500k", 500_000L, T1)), null, 0L);
        assertThat(oversizedAmount.promotionDiscount()).isEqualTo(120_000L);
        assertThat(oversizedAmount.finalPrice()).isZero();
    }

    @Test
    void bestAutoPromotionWins() {
        AutoCandidate small = autoAmount("Small 30k", 30_000L, T1);
        AutoCandidate bigger = autoAmount("Bigger 50k", 50_000L, T2);
        QuoteCalculation highestDiscountWins = PricingEngine.calculate(
                200_000L, List.of(small, bigger), null, 5_000L);
        assertThat(highestDiscountWins.promotion().name()).isEqualTo("Bigger 50k");
        assertThat(highestDiscountWins.finalPrice()).isEqualTo(150_000L);

        // Equal effective discount -> earliest ends_at wins (E5)
        AutoCandidate endsLater = autoPercent("20% later", 20, null, T2);
        AutoCandidate endsEarlier = autoAmount("40k earlier", 40_000L, T1);
        QuoteCalculation tieBrokenByEndsAt = PricingEngine.calculate(
                200_000L, List.of(endsLater, endsEarlier), null, 5_000L);
        assertThat(tieBrokenByEndsAt.promotion().name()).isEqualTo("40k earlier");
        assertThat(tieBrokenByEndsAt.promotionDiscount()).isEqualTo(40_000L);
        assertThat(tieBrokenByEndsAt.finalPrice()).isEqualTo(160_000L);
    }

    @Test
    void voucherCombinationModes() {
        AutoCandidate auto20Pct = autoPercent("Auto 20%", 20, null, T1);

        // combinable = true stacks on promo_price (200k -> 160k -> 144k)
        QuoteCalculation combined = PricingEngine.calculate(
                200_000L,
                List.of(auto20Pct),
                voucherPercent("STACK10", 10, null, true),
                5_000L);
        assertThat(combined.discountApplied()).isEqualTo(DiscountApplied.COMBINED);
        assertThat(combined.promotionDiscount()).isEqualTo(40_000L);
        assertThat(combined.voucherDiscount()).isEqualTo(16_000L);
        assertThat(combined.finalPrice()).isEqualTo(144_000L);
        assertThat(combined.promotion()).isNotNull();
        assertThat(combined.voucher()).isNotNull();
        assertThat(combined.voucherNotBetter()).isFalse();

        // combinable = false where voucher is strictly cheaper (promo 160k vs voucher 150k)
        QuoteCalculation voucherWins = PricingEngine.calculate(
                200_000L,
                List.of(auto20Pct),
                voucherAmount("V50K", 50_000L, false),
                5_000L);
        assertThat(voucherWins.discountApplied()).isEqualTo(DiscountApplied.VOUCHER_ONLY);
        assertThat(voucherWins.promotionDiscount()).isZero();
        assertThat(voucherWins.voucherDiscount()).isEqualTo(50_000L);
        assertThat(voucherWins.finalPrice()).isEqualTo(150_000L);
        assertThat(voucherWins.promotion()).isNull();
        assertThat(voucherWins.voucher()).isNotNull();
        assertThat(voucherWins.voucher().code()).isEqualTo("V50K");
        assertThat(voucherWins.voucherNotBetter()).isFalse();

        // combinable = false where auto is strictly cheaper (promo 160k vs voucher 170k)
        QuoteCalculation autoWins = PricingEngine.calculate(
                200_000L,
                List.of(auto20Pct),
                voucherAmount("V30K", 30_000L, false),
                5_000L);
        assertThat(autoWins.discountApplied()).isEqualTo(DiscountApplied.PROMOTION_ONLY);
        assertThat(autoWins.finalPrice()).isEqualTo(160_000L);
    }

    @Test
    void unusedVoucherIsNotRecorded() {
        AutoCandidate auto20Pct = autoPercent("Auto 20%", 20, null, T1);

        // Strictly worse non-combinable voucher
        QuoteCalculation worseVoucher = PricingEngine.calculate(
                200_000L,
                List.of(auto20Pct),
                voucherAmount("SMALL10K", 10_000L, false),
                5_000L);
        assertThat(worseVoucher.discountApplied()).isEqualTo(DiscountApplied.PROMOTION_ONLY);
        assertThat(worseVoucher.voucher()).isNull();
        assertThat(worseVoucher.voucherDiscount()).isZero();
        assertThat(worseVoucher.voucherNotBetter()).isTrue();
        assertThat(worseVoucher.promotion()).isNotNull();
        assertThat(worseVoucher.promotionDiscount()).isEqualTo(40_000L);

        // Equal price non-combinable voucher is also not better -> auto stays, voucher not recorded
        QuoteCalculation tieVoucher = PricingEngine.calculate(
                200_000L,
                List.of(auto20Pct),
                voucherAmount("SAME40K", 40_000L, false),
                5_000L);
        assertThat(tieVoucher.discountApplied()).isEqualTo(DiscountApplied.PROMOTION_ONLY);
        assertThat(tieVoucher.voucher()).isNull();
        assertThat(tieVoucher.voucherDiscount()).isZero();
        assertThat(tieVoucher.voucherNotBetter()).isTrue();
    }

    @Test
    void autoPromotionStopsAtMinimum() {
        // Explicit minAmount = 5_000: 20_000 - 18_000 clamps at 5_000
        QuoteCalculation clamped = PricingEngine.calculate(
                20_000L, List.of(autoAmount("Auto 18k", 18_000L, T1)), null, 5_000L);
        assertThat(clamped.finalPrice()).isEqualTo(5_000L);
        assertThat(clamped.promotionDiscount()).isEqualTo(15_000L);
        assertThat(clamped.promotionFloored()).isTrue();
        assertThat(clamped.promotion()).isNotNull();

        // Explicit minAmount = 10_000: 20_000 - 18_000 clamps at 10_000
        QuoteCalculation clamped10k = PricingEngine.calculate(
                20_000L, List.of(autoAmount("Auto 18k", 18_000L, T1)), null, 10_000L);
        assertThat(clamped10k.finalPrice()).isEqualTo(10_000L);
        assertThat(clamped10k.promotionDiscount()).isEqualTo(10_000L);

        // Product already at floor gives 0 effective discount -> no promotion applied
        QuoteCalculation atFloor = PricingEngine.calculate(
                5_000L, List.of(autoAmount("Auto 2k", 2_000L, T1)), null, 5_000L);
        assertThat(atFloor.finalPrice()).isEqualTo(5_000L);
        assertThat(atFloor.promotionDiscount()).isZero();
        assertThat(atFloor.promotion()).isNull();
        assertThat(atFloor.discountApplied()).isEqualTo(DiscountApplied.NONE);

        // minAmount = 0 disables floor rule
        QuoteCalculation noFloor = PricingEngine.calculate(
                20_000L, List.of(autoAmount("Auto 18k", 18_000L, T1)), null, 0L);
        assertThat(noFloor.finalPrice()).isEqualTo(2_000L);
        assertThat(noFloor.promotionDiscount()).isEqualTo(18_000L);
        assertThat(noFloor.promotionFloored()).isFalse();
    }

    @Test
    void voucherBelowMinimumBecomesFree() {
        long minAmount = 5_000L;

        // Raw final = 4_999 (minAmount - 1) -> becomes 0
        QuoteCalculation oneBelowMin = PricingEngine.calculate(
                20_000L, List.of(), voucherAmount("V15001", 15_001L, false), minAmount);
        assertThat(oneBelowMin.finalPrice()).isZero();
        assertThat(oneBelowMin.voucherDiscount()).isEqualTo(20_000L);
        assertThat(oneBelowMin.discountApplied()).isEqualTo(DiscountApplied.VOUCHER_ONLY);

        // Raw final = 1 -> becomes 0
        QuoteCalculation oneVndLeft = PricingEngine.calculate(
                20_000L, List.of(), voucherAmount("V19999", 19_999L, false), minAmount);
        assertThat(oneVndLeft.finalPrice()).isZero();
        assertThat(oneVndLeft.voucherDiscount()).isEqualTo(20_000L);

        // Raw final = exact minAmount -> stays minAmount
        QuoteCalculation exactMin = PricingEngine.calculate(
                20_000L, List.of(), voucherAmount("V15000", 15_000L, false), minAmount);
        assertThat(exactMin.finalPrice()).isEqualTo(5_000L);
        assertThat(exactMin.voucherDiscount()).isEqualTo(15_000L);

        // Combinable voucher dropping promo_price below minAmount also becomes 0
        QuoteCalculation combinedBelowMin = PricingEngine.calculate(
                20_000L,
                List.of(autoAmount("Auto 10k", 10_000L, T1)),
                voucherAmount("V6K", 6_000L, true),
                minAmount);
        assertThat(combinedBelowMin.promotionDiscount()).isEqualTo(10_000L);
        assertThat(combinedBelowMin.voucherDiscount()).isEqualTo(10_000L);
        assertThat(combinedBelowMin.finalPrice()).isZero();
        assertThat(combinedBelowMin.discountApplied()).isEqualTo(DiscountApplied.COMBINED);

        // minAmount = 0 disables rule so 1 VND stays 1 VND
        QuoteCalculation floorDisabled = PricingEngine.calculate(
                20_000L, List.of(), voucherAmount("V19999", 19_999L, false), 0L);
        assertThat(floorDisabled.finalPrice()).isEqualTo(1L);
        assertThat(floorDisabled.voucherDiscount()).isEqualTo(19_999L);
    }
}
