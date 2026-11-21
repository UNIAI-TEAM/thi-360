package vn.unicom.thi360.commerce.pricing;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.pricing.PricingEngine.AutoCandidate;
import vn.unicom.thi360.commerce.pricing.PricingEngine.VoucherCandidate;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionCodeNormalizer;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRedemptionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Production implementation of {@link PriceCalculator} backed by {@code commerce.promotion*}, {@link PurchaseHistory},
 * {@link ClassMembershipPort}, and {@link CommercePricingProperties} (TM 7.2).
 */
@Service
@Transactional(readOnly = true)
public class RealPriceCalculator implements PriceCalculator {

    private final PromotionRepository promotionRepository;
    private final PromotionScopeItemRepository scopeItemRepository;
    private final PromotionAudienceRepository audienceRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final PurchaseHistory purchaseHistory;
    private final ClassMembershipPort classMembershipPort;
    private final CommercePricingProperties properties;
    private final Clock clock;

    public RealPriceCalculator(
            PromotionRepository promotionRepository,
            PromotionScopeItemRepository scopeItemRepository,
            PromotionAudienceRepository audienceRepository,
            PromotionRedemptionRepository redemptionRepository,
            PurchaseHistory purchaseHistory,
            ClassMembershipPort classMembershipPort,
            CommercePricingProperties properties,
            Clock clock) {
        this.promotionRepository = promotionRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.audienceRepository = audienceRepository;
        this.redemptionRepository = redemptionRepository;
        this.purchaseHistory = purchaseHistory;
        this.classMembershipPort = classMembershipPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PriceResult priceOf(PricedProduct product) {
        Objects.requireNonNull(product, "product must not be null");
        Instant now = clock.instant();
        List<Promotion> activeAutos = promotionRepository.findActiveAutoPromotions(now);
        Map<UUID, ScopeTargets> scopes = loadScopes(activeAutos);
        return computeDisplayPrice(product, activeAutos, scopes);
    }

    @Override
    public Map<UUID, PriceResult> priceOfAll(List<PricedProduct> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        List<Promotion> activeAutos = promotionRepository.findActiveAutoPromotions(now);
        Map<UUID, ScopeTargets> scopes = loadScopes(activeAutos);
        Map<UUID, PriceResult> results = new LinkedHashMap<>(products.size());
        for (PricedProduct product : products) {
            if (product != null && product.productKey() != null) {
                results.put(product.productKey(), computeDisplayPrice(product, activeAutos, scopes));
            }
        }
        return results;
    }

    @Override
    public QuoteCalculation calculateQuote(QuoteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.product(), "product must not be null");
        Instant now = request.now() != null ? request.now() : clock.instant();
        PricedProduct product = request.product();

        List<Promotion> activeAutos = (request.userId() != null && request.excludedPendingOrderId() != null)
                ? promotionRepository.findActiveAutoPromotionsExcludingPendingOrder(
                        now, request.userId(), request.excludedPendingOrderId())
                : promotionRepository.findActiveAutoPromotions(now);
        Map<UUID, ScopeTargets> autoScopes = loadScopes(activeAutos);
        List<AutoCandidate> matchingAutos = matchingAutoCandidates(product, activeAutos, autoScopes);

        VoucherCandidate voucher = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            voucher = resolveAndValidateVoucher(request, now);
        }

        return PricingEngine.calculate(product.listPriceVnd(), matchingAutos, voucher, properties.vnpayMinAmount());
    }

    @Override
    public QuoteCalculation previewPromotion(PricedProduct product, UUID promotionId) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Promotion target = promotionRepository.findByIdAndDeletedAtIsNull(promotionId)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        ScopeTargets targetScope = loadScopes(List.of(target)).getOrDefault(target.getId(), ScopeTargets.EMPTY);
        boolean appliesToProduct = matchesScope(target, targetScope, product);

        if (target.getKind() == PromotionKind.AUTO) {
            List<AutoCandidate> candidates = appliesToProduct ? List.of(toAutoCandidate(target)) : List.of();
            return PricingEngine.calculate(product.listPriceVnd(), candidates, null, properties.vnpayMinAmount());
        }

        Instant now = clock.instant();
        List<Promotion> activeAutos = promotionRepository.findActiveAutoPromotions(now);
        Map<UUID, ScopeTargets> autoScopes = loadScopes(activeAutos);
        List<AutoCandidate> matchingAutos = matchingAutoCandidates(product, activeAutos, autoScopes);
        VoucherCandidate voucher = appliesToProduct ? toVoucherCandidate(target) : null;
        return PricingEngine.calculate(product.listPriceVnd(), matchingAutos, voucher, properties.vnpayMinAmount());
    }

    private PriceResult computeDisplayPrice(PricedProduct product, List<Promotion> activeAutos,
            Map<UUID, ScopeTargets> scopes) {
        List<AutoCandidate> matching = matchingAutoCandidates(product, activeAutos, scopes);
        QuoteCalculation quote = PricingEngine.calculate(
                product.listPriceVnd(), matching, null, properties.vnpayMinAmount());
        return quote.toPriceResult();
    }

    private List<AutoCandidate> matchingAutoCandidates(PricedProduct product, List<Promotion> promotions,
            Map<UUID, ScopeTargets> scopes) {
        if (promotions.isEmpty()) {
            return List.of();
        }
        List<AutoCandidate> matching = new ArrayList<>();
        for (Promotion promo : promotions) {
            ScopeTargets targets = scopes.getOrDefault(promo.getId(), ScopeTargets.EMPTY);
            if (matchesScope(promo, targets, product)) {
                matching.add(toAutoCandidate(promo));
            }
        }
        return matching;
    }

    private VoucherCandidate resolveAndValidateVoucher(QuoteRequest request, Instant now) {
        String normalizedCode = PromotionCodeNormalizer.normalize(request.voucherCode());
        Promotion promo = promotionRepository.findByCodeNormalizedAndKind(normalizedCode, PromotionKind.VOUCHER)
                .orElse(null);

        if (promo == null || promo.isDeleted() || promo.getStatus() != PromotionStatus.ACTIVE
                || now.isBefore(promo.getStartsAt())) {
            throw new BusinessRuleException("VOUCHER_INVALID");
        }
        if (!now.isBefore(promo.getEndsAt())) {
            throw new BusinessRuleException("VOUCHER_EXPIRED");
        }

        if (promo.getTotalLimit() != null) {
            boolean excludedOwn = request.userId() != null
                    && request.excludedPendingOrderId() != null
                    && redemptionRepository.existsByPromotionIdAndUserIdAndOrderIdAndState(
                            promo.getId(), request.userId(), request.excludedPendingOrderId(),
                            RedemptionState.RESERVED);
            int effectiveUsed = Math.max(0, promo.getUsedCount() - (excludedOwn ? 1 : 0));
            if (effectiveUsed >= promo.getTotalLimit()) {
                throw new BusinessRuleException("VOUCHER_EXHAUSTED");
            }
        }

        if (promo.getPerUserLimit() != null) {
            if (request.userId() == null) {
                throw new BusinessRuleException("VOUCHER_USER_LIMIT");
            }
            long userCount = redemptionRepository.countActiveForUserExcludingPendingOrder(
                    promo.getId(), request.userId(), request.excludedPendingOrderId());
            if (userCount >= promo.getPerUserLimit()) {
                throw new BusinessRuleException("VOUCHER_USER_LIMIT");
            }
        }

        ScopeTargets scopeTargets = loadScopes(List.of(promo)).getOrDefault(promo.getId(), ScopeTargets.EMPTY);
        if (!matchesScope(promo, scopeTargets, request.product())) {
            throw new BusinessRuleException("VOUCHER_NOT_APPLICABLE");
        }

        if (!isAudienceEligible(promo, request.userId())) {
            throw new BusinessRuleException("VOUCHER_NOT_ELIGIBLE");
        }

        return toVoucherCandidate(promo);
    }

    private boolean isAudienceEligible(Promotion promo, UUID userId) {
        PromotionAudienceType audience = promo.getAudience();
        if (audience == null || audience == PromotionAudienceType.ALL) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (audience == PromotionAudienceType.NEW_ACCOUNT) {
            return !purchaseHistory.hasFulfilledOrder(userId);
        }
        // SELECTED audience: direct user_id match or current membership in an allowed class_id (TM 7.1, AC-13).
        List<PromotionAudience> targets = audienceRepository.findByPromotionId(promo.getId());
        Set<UUID> allowedClassIds = new HashSet<>();
        for (PromotionAudience target : targets) {
            if (userId.equals(target.getUserId())) {
                return true;
            }
            if (target.getClassId() != null) {
                allowedClassIds.add(target.getClassId());
            }
        }
        if (allowedClassIds.isEmpty()) {
            return false;
        }
        return classMembershipPort.findCurrentClassesByUserIds(List.of(userId))
                .getOrDefault(userId, List.of()).stream()
                .anyMatch(c -> allowedClassIds.contains(c.id()));
    }

    private Map<UUID, ScopeTargets> loadScopes(List<Promotion> promotions) {
        List<UUID> scopedIds = promotions.stream()
                .filter(p -> p.getScope() == PromotionScope.EXAM_KIND
                        || p.getScope() == PromotionScope.EXAM
                        || p.getScope() == PromotionScope.BUNDLE)
                .map(Promotion::getId)
                .toList();
        if (scopedIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ScopeTargetsBuilder> builders = new HashMap<>(scopedIds.size());
        for (PromotionScopeItem item : scopeItemRepository.findByPromotionIdIn(scopedIds)) {
            ScopeTargetsBuilder builder = builders.computeIfAbsent(item.getPromotionId(), id -> new ScopeTargetsBuilder());
            if (item.getExamItemId() != null) {
                builder.examKindItemIds.add(item.getExamItemId());
            }
            if (item.getExamId() != null) {
                builder.examIds.add(item.getExamId());
            }
            if (item.getBundleId() != null) {
                builder.bundleIds.add(item.getBundleId());
            }
        }
        Map<UUID, ScopeTargets> result = new HashMap<>(builders.size());
        builders.forEach((id, b) -> result.put(id, b.build()));
        return result;
    }

    private static boolean matchesScope(Promotion promo, ScopeTargets targets, PricedProduct product) {
        return switch (promo.getScope()) {
            case ALL -> true;
            case EXAM_KIND -> product.examKindItemId() != null
                    && targets.examKindItemIds().contains(product.examKindItemId());
            case EXAM -> product.examId() != null
                    && targets.examIds().contains(product.examId());
            case BUNDLE -> product.bundleId() != null
                    && targets.bundleIds().contains(product.bundleId());
        };
    }

    private static AutoCandidate toAutoCandidate(Promotion promo) {
        return new AutoCandidate(promo.getId(), promo.getName(), promo.getDiscountType(),
                promo.getDiscountValue(), promo.getMaxDiscountVnd(), promo.getEndsAt());
    }

    private static VoucherCandidate toVoucherCandidate(Promotion promo) {
        return new VoucherCandidate(promo.getId(), promo.getCode(), promo.getName(), promo.getDiscountType(),
                promo.getDiscountValue(), promo.getMaxDiscountVnd(), promo.isCombinable());
    }

    private record ScopeTargets(Set<UUID> examKindItemIds, Set<UUID> examIds, Set<UUID> bundleIds) {
        static final ScopeTargets EMPTY = new ScopeTargets(Set.of(), Set.of(), Set.of());
    }

    private static final class ScopeTargetsBuilder {
        final Set<UUID> examKindItemIds = new HashSet<>();
        final Set<UUID> examIds = new HashSet<>();
        final Set<UUID> bundleIds = new HashSet<>();

        ScopeTargets build() {
            return new ScopeTargets(Set.copyOf(examKindItemIds), Set.copyOf(examIds), Set.copyOf(bundleIds));
        }
    }
}
