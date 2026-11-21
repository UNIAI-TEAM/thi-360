package vn.unicom.thi360.commerce.promotion.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.pricing.CommercePricingProperties;
import vn.unicom.thi360.commerce.pricing.PricingEngine;
import vn.unicom.thi360.commerce.pricing.PricingEngine.AutoCandidate;
import vn.unicom.thi360.commerce.pricing.PricingEngine.AutoEvaluation;
import vn.unicom.thi360.commerce.promotion.api.CapWarning;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.CapWarningProduct;

/**
 * Evaluates {@link CapWarning} entries for automatic promotions whose discount is clamped by {@code vnpayMinAmount}
 * (BS §10, AC-41). Batches on-sale storefront exam reads so list pages evaluate in one query.
 */
@Service
public class PromotionCapWarningService {

    private final StorefrontProductRepository storefrontProductRepository;
    private final PromotionScopeItemRepository scopeItemRepository;
    private final CommercePricingProperties pricingProperties;

    public PromotionCapWarningService(
            StorefrontProductRepository storefrontProductRepository,
            PromotionScopeItemRepository scopeItemRepository,
            CommercePricingProperties pricingProperties) {
        this.storefrontProductRepository = storefrontProductRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.pricingProperties = pricingProperties;
    }

    /** Computes cap warnings for a page of promotions using a single batch load of on-sale exams. */
    public Map<UUID, List<CapWarning>> computeForPage(List<Promotion> promotions) {
        long minAmount = pricingProperties.vnpayMinAmount();
        if (minAmount <= 0 || promotions == null || promotions.isEmpty()) {
            return Map.of();
        }
        List<Promotion> autos = promotions.stream()
                .filter(p -> p.getKind() == PromotionKind.AUTO)
                .toList();
        if (autos.isEmpty()) {
            return Map.of();
        }

        List<CapWarningProduct> products = storefrontProductRepository.listOnSaleProductsForCapWarnings();
        if (products.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ScopeSet> scopesByPromo = loadScopes(autos);
        Map<UUID, List<CapWarning>> result = new LinkedHashMap<>(autos.size());
        for (Promotion promo : autos) {
            ScopeSet scopeSet = scopesByPromo.getOrDefault(promo.getId(), ScopeSet.EMPTY);
            result.put(promo.getId(), evaluate(promo, scopeSet, products, minAmount));
        }
        return result;
    }

    /** Computes cap warnings for a single promotion whose scope items are already loaded. */
    public List<CapWarning> computeForOne(Promotion promo, List<PromotionScopeItem> scopeItems) {
        long minAmount = pricingProperties.vnpayMinAmount();
        if (minAmount <= 0 || promo == null || promo.getKind() != PromotionKind.AUTO) {
            return List.of();
        }
        List<CapWarningProduct> products = storefrontProductRepository.listOnSaleProductsForCapWarnings();
        if (products.isEmpty()) {
            return List.of();
        }
        ScopeSet scopeSet = ScopeSet.from(scopeItems);
        return evaluate(promo, scopeSet, products, minAmount);
    }

    private Map<UUID, ScopeSet> loadScopes(List<Promotion> autos) {
        List<UUID> scopedIds = autos.stream()
                .filter(p -> p.getScope() == PromotionScope.EXAM_KIND
                        || p.getScope() == PromotionScope.EXAM
                        || p.getScope() == PromotionScope.BUNDLE)
                .map(Promotion::getId)
                .toList();
        if (scopedIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PromotionScopeItem>> grouped = new HashMap<>(scopedIds.size());
        for (PromotionScopeItem item : scopeItemRepository.findByPromotionIdIn(scopedIds)) {
            grouped.computeIfAbsent(item.getPromotionId(), k -> new ArrayList<>()).add(item);
        }
        Map<UUID, ScopeSet> scopes = new HashMap<>(grouped.size());
        grouped.forEach((id, items) -> scopes.put(id, ScopeSet.from(items)));
        return scopes;
    }

    private static List<CapWarning> evaluate(
            Promotion promo, ScopeSet scopeSet, List<CapWarningProduct> products, long minAmount) {
        AutoCandidate candidate = new AutoCandidate(
                promo.getId(),
                promo.getName(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getMaxDiscountVnd(),
                promo.getEndsAt());
        List<CapWarning> warnings = new ArrayList<>();
        for (CapWarningProduct product : products) {
            if (!matchesScope(promo.getScope(), scopeSet, product) || product.targetId() == null) {
                continue;
            }
            AutoEvaluation eval = PricingEngine.evaluateAuto(product.listPriceVnd(), candidate, minAmount);
            if (!eval.floored()) {
                continue;
            }
            long rawDiscount = PricingEngine.computeRawDiscount(
                    product.listPriceVnd(),
                    promo.getDiscountType(),
                    promo.getDiscountValue(),
                    promo.getMaxDiscountVnd());
            long discountedPrice = Math.max(0L, product.listPriceVnd() - rawDiscount);
            warnings.add(new CapWarning(
                    product.targetId(),
                    product.name(),
                    product.listPriceVnd(),
                    discountedPrice,
                    eval.promoPrice()));
        }
        warnings.sort(Comparator.comparing(CapWarning::examName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CapWarning::examId));
        return List.copyOf(warnings);
    }

    private static boolean matchesScope(PromotionScope scope, ScopeSet targets, CapWarningProduct product) {
        return switch (scope) {
            case ALL -> true;
            case EXAM_KIND -> product.examKindItemId() != null
                    && targets.examKindIds().contains(product.examKindItemId());
            case EXAM -> product.productType() == ProductType.EXAM
                    && product.examId() != null
                    && targets.examIds().contains(product.examId());
            case BUNDLE -> product.productType() == ProductType.BUNDLE
                    && product.bundleId() != null
                    && targets.bundleIds().contains(product.bundleId());
        };
    }

    private record ScopeSet(Set<UUID> examKindIds, Set<UUID> examIds, Set<UUID> bundleIds) {
        static final ScopeSet EMPTY = new ScopeSet(Set.of(), Set.of(), Set.of());

        static ScopeSet from(List<PromotionScopeItem> items) {
            if (items == null || items.isEmpty()) {
                return EMPTY;
            }
            Set<UUID> kinds = new HashSet<>();
            Set<UUID> exams = new HashSet<>();
            Set<UUID> bundles = new HashSet<>();
            for (PromotionScopeItem item : items) {
                if (item.getExamItemId() != null) {
                    kinds.add(item.getExamItemId());
                }
                if (item.getExamId() != null) {
                    exams.add(item.getExamId());
                }
                if (item.getBundleId() != null) {
                    bundles.add(item.getBundleId());
                }
            }
            return new ScopeSet(Set.copyOf(kinds), Set.copyOf(exams), Set.copyOf(bundles));
        }
    }
}
