package vn.unicom.thi360.commerce.pricing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteRequest;
import vn.unicom.thi360.commerce.storefront.application.ProductDiscontinuedException;
import vn.unicom.thi360.commerce.storefront.application.StorefrontErrorCodes;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Product lookup and price-quote calculation service backing {@code POST /api/v1/me/price-quote} (TM 10.2).
 */
@Service
@Transactional(readOnly = true)
public class PriceQuoteService {

    private final StorefrontProductRepository storefrontProductRepository;
    private final PriceCalculator priceCalculator;
    private final PendingPaymentOrderLookup pendingPaymentOrderLookup;

    public PriceQuoteService(
            StorefrontProductRepository storefrontProductRepository,
            PriceCalculator priceCalculator) {
        this(storefrontProductRepository, priceCalculator, (userId, code) -> Optional.empty());
    }

    @Autowired
    public PriceQuoteService(
            StorefrontProductRepository storefrontProductRepository,
            PriceCalculator priceCalculator,
            PendingPaymentOrderLookup pendingPaymentOrderLookup) {
        this.storefrontProductRepository = Objects.requireNonNull(storefrontProductRepository);
        this.priceCalculator = Objects.requireNonNull(priceCalculator);
        this.pendingPaymentOrderLookup = Objects.requireNonNull(pendingPaymentOrderLookup);
    }

    /** Resolves an on-sale product by code, throwing 404 for unknown/unavailable and 410 for discontinued (AC-35). */
    public StoredProduct requireOnSaleProduct(String code) {
        StoredProduct stored = storefrontProductRepository.findByCode(StorefrontSearchText.normalize(code))
                .orElse(null);
        if (stored == null || stored.saleState() == SaleState.UNAVAILABLE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        if (stored.saleState() == SaleState.DISCONTINUED) {
            throw new ProductDiscontinuedException(stored.code(), stored.name());
        }
        if (stored.saleState() != SaleState.ON_SALE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        return stored;
    }

    /** Calculates the learner's quote and whether price or version changed since the caller last saw it (AC-34). */
    public PriceQuote quote(UUID userId, StoredProduct stored, PriceQuoteRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(stored, "stored must not be null");
        Objects.requireNonNull(request, "request must not be null");

        PricedProduct product = new PricedProduct(
                stored.examId(), stored.bundleId(), stored.examKindItemId(), stored.listPriceVnd());
        UUID pendingOrderId = pendingPaymentOrderLookup
                .findPendingPaymentOrderId(userId, StorefrontSearchText.normalize(stored.code()))
                .orElse(null);
        QuoteCalculation calc = priceCalculator.calculateQuote(
                new QuoteRequest(product, userId, request.voucherCode(), pendingOrderId, null));
        boolean changed = (request.seenVersionNo() != null && !request.seenVersionNo().equals(stored.versionNo()))
                || (request.seenPrice() != null && !request.seenPrice().equals(calc.finalPrice()));
        return PriceQuote.from(stored, calc, changed);
    }
}
