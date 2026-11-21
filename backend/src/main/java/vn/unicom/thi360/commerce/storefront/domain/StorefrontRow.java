package vn.unicom.thi360.commerce.storefront.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.order.domain.ProductType;

/**
 * One projected row of {@code commerce.storefront_product} (TM 10.1, 16.2) as the projection writes it. {@code card} and
 * {@code detail} are the JSON of the public models without the promotion price. {@code projectionHash} covers every
 * other field, so a projection that changes nothing is recognised and leaves the stored row alone.
 */
public record StorefrontRow(
        UUID examId,
        UUID bundleId,
        ProductType productType,
        String code,
        String codeNormalized,
        int versionNo,
        SaleState saleState,
        String name,
        String searchText,
        UUID examItemId,
        String kindCode,
        String scope,
        UUID scopeItemId,
        List<UUID> subjectItemIds,
        List<String> partCodes,
        List<String> partNames,
        Integer questionCount,
        Integer questionCountMax,
        Integer durationMinutes,
        Integer durationMinutesMax,
        BigDecimal maxPoints,
        BigDecimal maxPointsMax,
        String buildMode,
        long listPriceVnd,
        int attemptsGranted,
        UUID coverAssetId,
        String card,
        String detail,
        String projectionHash,
        Instant publishedAt) {

    public StorefrontRow(
            UUID examId,
            String code,
            String codeNormalized,
            int versionNo,
            SaleState saleState,
            String name,
            String searchText,
            UUID examItemId,
            String kindCode,
            String scope,
            UUID scopeItemId,
            List<UUID> subjectItemIds,
            List<String> partCodes,
            List<String> partNames,
            Integer questionCount,
            Integer questionCountMax,
            Integer durationMinutes,
            Integer durationMinutesMax,
            BigDecimal maxPoints,
            BigDecimal maxPointsMax,
            String buildMode,
            long listPriceVnd,
            int attemptsGranted,
            UUID coverAssetId,
            String card,
            String detail,
            String projectionHash,
            Instant publishedAt) {
        this(examId, null, ProductType.EXAM, code, codeNormalized, versionNo, saleState, name, searchText, examItemId,
                kindCode, scope, scopeItemId, subjectItemIds, partCodes, partNames, questionCount, questionCountMax,
                durationMinutes, durationMinutesMax, maxPoints, maxPointsMax, buildMode, listPriceVnd, attemptsGranted,
                coverAssetId, card, detail, projectionHash, publishedAt);
    }

    public StorefrontRow {
        productType = productType == null ? ProductType.EXAM : productType;
        subjectItemIds = subjectItemIds == null ? List.of() : List.copyOf(subjectItemIds);
        partCodes = partCodes == null ? List.of() : List.copyOf(partCodes);
        partNames = partNames == null ? List.of() : List.copyOf(partNames);
    }
}
