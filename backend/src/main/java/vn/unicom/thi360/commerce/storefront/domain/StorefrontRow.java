package vn.unicom.thi360.commerce.storefront.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One projected row of {@code commerce.storefront_product} (TM 10.1) as the projection writes it. {@code card} and
 * {@code detail} are the JSON of the public models without the promotion price. {@code projectionHash} covers every
 * other field, so a projection that changes nothing is recognised and leaves the stored row alone.
 */
public record StorefrontRow(
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

    public StorefrontRow {
        subjectItemIds = List.copyOf(subjectItemIds);
        partCodes = List.copyOf(partCodes);
        partNames = List.copyOf(partNames);
    }
}
