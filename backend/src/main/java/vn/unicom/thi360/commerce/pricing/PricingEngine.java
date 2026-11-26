package vn.unicom.thi360.commerce.pricing;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.AppliedAutoPromotion;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.AppliedVoucher;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;

/**
 * Pure, stateless pricing math engine implementing TM 7.2 steps 1–5 and E5–E7, E26.
 *
 * <p>Kept independent of Spring/DB so {@code PriceCalculatorTest} can verify every boundary rule without containers,
 * and shared with {@link RealPriceCalculator} and cap-warning calculations.
 */
public final class PricingEngine {

    private static final BigInteger HUNDRED = BigInteger.valueOf(100L);

    private PricingEngine() {
    }

    public record AutoCandidate(
            UUID id,
            String name,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            Instant endsAt
    ) {
    }

    public record VoucherCandidate(
            UUID id,
            String code,
            String name,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            boolean combinable
    ) {
    }

    public record AutoEvaluation(
            AutoCandidate candidate,
            long promoPrice,
            long effectiveDiscount,
            boolean floored
    ) {
    }

    /** Computes raw discount floored to integer VND (TM E7), capped at {@code maxDiscountVnd} and {@code basePrice}. */
    public static long computeRawDiscount(long basePrice, DiscountType type, long value, Long maxDiscountVnd) {
        if (basePrice <= 0 || value <= 0 || type == null) {
            return 0L;
        }
        long discount;
        if (type == DiscountType.PERCENT) {
            long pct = Math.min(100L, value);
            discount = BigInteger.valueOf(basePrice)
                    .multiply(BigInteger.valueOf(pct))
                    .divide(HUNDRED)
                    .longValueExact();
            if (maxDiscountVnd != null && maxDiscountVnd > 0) {
                discount = Math.min(discount, maxDiscountVnd);
            }
        } else {
            discount = value;
        }
        return Math.max(0L, Math.min(basePrice, discount));
    }

    /**
     * Evaluates one automatic promotion with the E26 minimum-amount floor clamp. A promotion that removes the whole
     * price makes the product free (0 VND, no payment) and is not clamped (UNI-2817, M.Trường 2026-11-26); a price
     * left strictly between 0 and the minimum is still raised to the minimum.
     */
    public static AutoEvaluation evaluateAuto(long listPrice, AutoCandidate candidate, long minAmount) {
        long safeList = Math.max(0L, listPrice);
        long rawDiscount = computeRawDiscount(safeList, candidate.discountType(), candidate.discountValue(),
                candidate.maxDiscountVnd());
        long floor = minAmount > 0 ? Math.min(safeList, minAmount) : 0L;
        long rawAfter = Math.max(0L, safeList - rawDiscount);
        boolean free = rawDiscount > 0 && rawAfter == 0L;
        boolean floored = minAmount > 0 && rawDiscount > 0 && !free && rawAfter < floor;
        long promoPrice = free ? 0L : Math.max(rawAfter, floor);
        long effectiveDiscount = Math.max(0L, safeList - promoPrice);
        return new AutoEvaluation(candidate, promoPrice, effectiveDiscount, floored);
    }

    /** Picks the winning automatic promotion (E5): highest effective discount, then earliest {@code endsAt}. */
    public static AutoEvaluation selectBestAuto(long listPrice, List<AutoCandidate> candidates, long minAmount) {
        if (candidates == null || candidates.isEmpty() || listPrice <= 0) {
            return null;
        }
        return candidates.stream()
                .map(c -> evaluateAuto(listPrice, c, minAmount))
                .filter(eval -> eval.effectiveDiscount() > 0)
                .min(Comparator
                        .comparingLong(AutoEvaluation::effectiveDiscount).reversed()
                        .thenComparing(eval -> eval.candidate().endsAt())
                        .thenComparing(eval -> eval.candidate().id()))
                .orElse(null);
    }

    /** Applies E26 voucher floor: strictly positive amounts below {@code minAmount} become 0 VND. */
    public static long applyVoucherFloor(long rawFinal, long minAmount) {
        if (rawFinal <= 0) {
            return 0L;
        }
        if (minAmount > 0 && rawFinal < minAmount) {
            return 0L;
        }
        return rawFinal;
    }

    /** Computes the full quote from list price, eligible auto-promotions, optional eligible voucher, and floor. */
    public static QuoteCalculation calculate(long listPrice, List<AutoCandidate> autoCandidates,
            VoucherCandidate voucher, long minAmount) {
        long safeList = Math.max(0L, listPrice);
        AutoEvaluation bestAuto = selectBestAuto(safeList, autoCandidates, minAmount);
        long promoPrice = bestAuto != null ? bestAuto.promoPrice() : safeList;
        long autoDiscount = bestAuto != null ? bestAuto.effectiveDiscount() : 0L;
        boolean floored = bestAuto != null && bestAuto.floored();

        if (voucher == null) {
            if (bestAuto == null) {
                return new QuoteCalculation(safeList, safeList, null, 0L, null, 0L,
                        DiscountApplied.NONE, false, false);
            }
            return new QuoteCalculation(safeList, promoPrice, toAppliedAuto(bestAuto), autoDiscount, null, 0L,
                    DiscountApplied.PROMOTION_ONLY, false, floored);
        }

        if (voucher.combinable()) {
            long base = promoPrice;
            long rawVoucherDiscount = computeRawDiscount(base, voucher.discountType(),
                    voucher.discountValue(), voucher.maxDiscountVnd());
            long finalPrice = rawVoucherDiscount > 0L
                    ? applyVoucherFloor(Math.max(0L, base - rawVoucherDiscount), minAmount)
                    : base;
            long voucherDiscount = Math.max(0L, base - finalPrice);

            if (voucherDiscount == 0L) {
                if (bestAuto == null) {
                    return new QuoteCalculation(safeList, base, null, 0L, null, 0L,
                            DiscountApplied.NONE, false, false);
                }
                return new QuoteCalculation(safeList, promoPrice, toAppliedAuto(bestAuto), autoDiscount,
                        null, 0L, DiscountApplied.PROMOTION_ONLY, true, floored);
            }
            if (bestAuto == null) {
                return new QuoteCalculation(safeList, finalPrice, null, 0L,
                        toAppliedVoucher(voucher, voucherDiscount), voucherDiscount,
                        DiscountApplied.VOUCHER_ONLY, false, false);
            }
            return new QuoteCalculation(safeList, finalPrice, toAppliedAuto(bestAuto), autoDiscount,
                    toAppliedVoucher(voucher, voucherDiscount), voucherDiscount,
                    DiscountApplied.COMBINED, false, floored);
        }

        long rawVoucherDiscount = computeRawDiscount(safeList, voucher.discountType(),
                voucher.discountValue(), voucher.maxDiscountVnd());
        long voucherOnlyPrice = rawVoucherDiscount > 0L
                ? applyVoucherFloor(Math.max(0L, safeList - rawVoucherDiscount), minAmount)
                : safeList;
        long voucherOnlyDiscount = Math.max(0L, safeList - voucherOnlyPrice);

        if (voucherOnlyDiscount > 0L && (bestAuto == null || voucherOnlyPrice < promoPrice)) {
            return new QuoteCalculation(safeList, voucherOnlyPrice, null, 0L,
                    toAppliedVoucher(voucher, voucherOnlyDiscount), voucherOnlyDiscount,
                    DiscountApplied.VOUCHER_ONLY, false, false);
        }
        if (bestAuto == null) {
            return new QuoteCalculation(safeList, safeList, null, 0L, null, 0L,
                    DiscountApplied.NONE, false, false);
        }
        return new QuoteCalculation(safeList, promoPrice, toAppliedAuto(bestAuto), autoDiscount,
                null, 0L, DiscountApplied.PROMOTION_ONLY, true, floored);
    }

    private static AppliedAutoPromotion toAppliedAuto(AutoEvaluation eval) {
        AutoCandidate c = eval.candidate();
        return new AppliedAutoPromotion(c.id(), c.name(), c.discountType(), c.discountValue(),
                c.maxDiscountVnd(), c.endsAt(), eval.effectiveDiscount());
    }

    private static AppliedVoucher toAppliedVoucher(VoucherCandidate v, long discountVnd) {
        return new AppliedVoucher(v.id(), v.code(), v.name(), v.discountType(), v.discountValue(),
                v.maxDiscountVnd(), v.combinable(), discountVnd);
    }
}
