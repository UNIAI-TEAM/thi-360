package vn.unicom.thi360.commerce.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PriceResult;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.FormatFilter;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.KindFilter;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.PartFilter;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.SubjectFilter;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontFormat;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSort;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.ListQuery;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.ListedProduct;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.SortableProduct;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * The public reads of the storefront (TM 10.2). They read only {@code commerce.storefront_product}, so a draft, a
 * question or an answer is never within reach; the only call out is the cover image, through the port.
 */
@Service
@Transactional(readOnly = true)
public class StorefrontQueryService {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";

    private final StorefrontProductRepository repository;
    private final CatalogLookup catalog;
    private final PriceCalculator prices;
    private final PublishedExamPort port;
    private final ObjectMapper mapper;
    private final AppMessages messages;

    public StorefrontQueryService(StorefrontProductRepository repository, CatalogLookup catalog,
            PriceCalculator prices, PublishedExamPort port, ObjectMapper mapper, AppMessages messages) {
        this.repository = repository;
        this.catalog = catalog;
        this.prices = prices;
        this.port = port;
        this.mapper = mapper;
        this.messages = messages;
    }

    /** The filters of the catalog page: only values that have an on-sale product (a count of 0 never appears). */
    public StorefrontFilters filters() {
        Map<String, Long> kindCounts = new LinkedHashMap<>();
        repository.countByKind().forEach(c -> kindCounts.put(c.key(), c.count()));
        List<KindFilter> kinds = new ArrayList<>();
        for (CatalogItemView item : catalog.listGroupItems(EXAM_GROUP)) {
            Long count = kindCounts.remove(item.code());
            if (count != null && count > 0) {
                kinds.add(new KindFilter(item.code(), item.nameVi(), item.nameEn(), count));
            }
        }
        // A kind that left the catalog still has products on sale: show it by its code rather than hide the products.
        kindCounts.forEach((code, count) -> kinds.add(new KindFilter(code, code, code, count)));

        Map<String, Long> scopeCounts = new LinkedHashMap<>();
        repository.countByScope().forEach(c -> scopeCounts.put(c.key(), c.count()));
        List<FormatFilter> formats = new ArrayList<>();
        for (StorefrontFormat format : StorefrontFormat.values()) {
            Long count = scopeCounts.get(format.name());
            if (count != null && count > 0) {
                formats.add(new FormatFilter(format.name(), count));
            }
        }

        Map<UUID, Long> subjectCounts = new LinkedHashMap<>();
        repository.countBySubject().forEach(c -> subjectCounts.put(c.subjectItemId(), c.count()));
        List<SubjectFilter> subjects = new ArrayList<>();
        for (CatalogItemView item : catalog.listGroupItems(SUBJECT_GROUP)) {
            Long count = subjectCounts.get(item.id());
            if (count != null && count > 0) {
                subjects.add(new SubjectFilter(item.code(), item.nameVi(), item.nameEn(), count));
            }
        }

        List<PartFilter> parts = repository.countByPart().stream()
                .map(p -> new PartFilter(p.kindCode(), p.code(), p.name(), p.count())).toList();
        return new StorefrontFilters(kinds, formats, subjects, parts);
    }

    /**
     * A page of on-sale products. {@code page} counts from 0 (like {@code PageQuery}); a {@code size} above 48 is cut to
     * 48, not refused. Prices are computed now, so a promotion that starts or ends shows without a rebuild.
     */
    public PageResponse<ProductCard> products(String q, String kind, StorefrontFormat format, String subject,
            String part, StorefrontSort sort, Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        if (resolvedPage < 0) {
            throw new ValidationException(List.of(
                    new FieldError("page", "INVALID", messages.get("validation.pageQuery.page.min"))));
        }
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedSize < 1) {
            throw new ValidationException(List.of(
                    new FieldError("size", "INVALID", messages.get("validation.pageQuery.size.min"))));
        }
        resolvedSize = Math.min(resolvedSize, MAX_SIZE);

        String normalized = StorefrontSearchText.normalize(
                q == null || q.length() <= MAX_QUERY_LENGTH ? q : q.substring(0, MAX_QUERY_LENGTH));
        String likePattern = normalized.isEmpty() ? null : StorefrontSearchText.containsPattern(normalized);

        UUID subjectItemId = null;
        if (subject != null && !subject.isBlank()) {
            subjectItemId = subjectId(subject);
            if (subjectItemId == null) {
                return new PageResponse<>(List.of(), resolvedPage, resolvedSize, 0, 0);
            }
        }
        StorefrontSort resolvedSort = sort == null ? StorefrontSort.NEWEST : sort;
        ListQuery query = new ListQuery(likePattern, blankToNull(resolveKind(kind)),
                format == null ? null : format.name(), subjectItemId, blankToNull(part),
                resolvedSort, resolvedPage, resolvedSize);
        if (resolvedSort == StorefrontSort.NEWEST) {
            StorefrontProductRepository.Page result = repository.list(query);
            List<PricedProduct> priced = result.items().stream()
                    .map(item -> new PricedProduct(item.examId(), item.examKindItemId(), item.listPriceVnd()))
                    .toList();
            Map<UUID, PriceResult> priceMap = prices.priceOfAll(priced);
            List<ProductCard> cards = result.items().stream().map(item -> {
                ProductCard card = read(item.card(), ProductCard.class);
                return card.withPrice(priceMap.get(item.examId()));
            }).toList();
            int totalPages = (int) ((result.total() + resolvedSize - 1) / resolvedSize);
            return new PageResponse<>(cards, resolvedPage, resolvedSize, result.total(), totalPages);
        }

        List<SortableProduct> all = repository.listSortable(query);
        long total = all.size();
        if (all.isEmpty()) {
            return new PageResponse<>(List.of(), resolvedPage, resolvedSize, 0, 0);
        }
        Map<UUID, PriceResult> priceMap = prices.priceOfAll(all.stream()
                .map(item -> new PricedProduct(item.examId(), item.examKindItemId(), item.listPriceVnd()))
                .toList());

        Comparator<SortableProduct> priceComparator = Comparator.comparingLong(
                item -> priceMap.get(item.examId()).price());
        if (resolvedSort == StorefrontSort.PRICE_DESC) {
            priceComparator = priceComparator.reversed();
        }
        Comparator<SortableProduct> fullComparator = priceComparator
                .thenComparing(SortableProduct::publishedAt, Comparator.reverseOrder())
                .thenComparing(SortableProduct::id, StorefrontQueryService::compareUuidUnsigned);

        List<SortableProduct> sorted = new ArrayList<>(all);
        sorted.sort(fullComparator);

        int fromIndex = (int) Math.min((long) resolvedPage * resolvedSize, total);
        int toIndex = (int) Math.min((long) fromIndex + resolvedSize, total);
        List<SortableProduct> slice = sorted.subList(fromIndex, toIndex);
        List<ProductCard> cards;
        if (slice.isEmpty()) {
            cards = List.of();
        } else {
            Map<UUID, String> cardJsonById = repository.findCardsByIds(
                    slice.stream().map(SortableProduct::id).toList());
            cards = slice.stream()
                    .map(item -> read(cardJsonById.get(item.id()), ProductCard.class)
                            .withPrice(priceMap.get(item.examId())))
                    .toList();
        }
        int totalPages = (int) ((total + resolvedSize - 1) / resolvedSize);
        return new PageResponse<>(cards, resolvedPage, resolvedSize, total, totalPages);
    }

    /**
     * The detail of a product. 404 for a code that has no row, and a draft has none, so a draft looks exactly like an
     * unknown code; 410 (with the product) for one that was discontinued.
     */
    public ProductDetail detail(String code) {
        StoredProduct stored = repository.findByCode(StorefrontSearchText.normalize(code)).orElse(null);
        if (stored == null) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        if (stored.saleState() == SaleState.DISCONTINUED) {
            throw new ProductDiscontinuedException(stored.code(), stored.name());
        }
        if (stored.saleState() != SaleState.ON_SALE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        ProductDetail detail = read(stored.detail(), ProductDetail.class);
        return detail.withPrice(prices.priceOf(
                new PricedProduct(stored.examId(), stored.examKindItemId(), stored.listPriceVnd())));
    }

    /**
     * The image of an asset that is the current cover of a storefront row; anything else is a 404. It runs without a
     * database transaction: the image comes from the object store, and no connection is held while it is read (plan B2).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PublishedExamPort.CoverImage cover(UUID assetId) {
        if (!repository.isCurrentCover(assetId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return port.openCover(assetId).orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    // ------------------------------------------------------------------ helpers

    private String resolveKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String wanted = kind.trim().toLowerCase(Locale.ROOT);
        for (CatalogItemView item : catalog.listGroupItems(EXAM_GROUP)) {
            if (item.code().toLowerCase(Locale.ROOT).equals(wanted)) {
                return item.code();
            }
        }
        return kind.trim();
    }

    private UUID subjectId(String subject) {
        String wanted = subject.trim().toLowerCase(Locale.ROOT);
        for (CatalogItemView item : catalog.listGroupItems(SUBJECT_GROUP)) {
            if (item.code().toLowerCase(Locale.ROOT).equals(wanted)) {
                return item.id();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int compareUuidUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored storefront model is not readable", e);
        }
    }
}
