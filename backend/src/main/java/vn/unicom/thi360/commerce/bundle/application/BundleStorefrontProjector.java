package vn.unicom.thi360.commerce.bundle.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.bundle.application.BundleExamSnapshotSupport.ExamMetrics;
import vn.unicom.thi360.commerce.bundle.application.BundleExamSnapshotSupport.SubjectCatalog;
import vn.unicom.thi360.commerce.bundle.application.BundleExamSnapshotSupport.SubjectInfo;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleCodeNormalizer;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.SnapshotItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.storefront.api.BundleDetailItem;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.DetailBenefits;
import vn.unicom.thi360.commerce.storefront.api.DetailScoring;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Builds and persists {@code commerce.storefront_product} rows for bundles from {@link BundleVersion} snapshots and
 * live serving exam metadata (TM 16.2, BS 2).
 */
@Component
public class BundleStorefrontProjector {

    private final StorefrontProductRepository storefrontRepository;
    private final BundleExamSnapshotSupport snapshotSupport;
    private final CatalogLookup catalogLookup;
    private final ObjectMapper mapper;

    public BundleStorefrontProjector(
            StorefrontProductRepository storefrontRepository,
            BundleExamSnapshotSupport snapshotSupport,
            CatalogLookup catalogLookup,
            ObjectMapper mapper) {
        this.storefrontRepository = storefrontRepository;
        this.snapshotSupport = snapshotSupport;
        this.catalogLookup = catalogLookup;
        this.mapper = mapper;
    }

    public boolean project(
            Bundle bundle,
            BundleVersion version,
            SaleState saleState,
            Map<UUID, ServingExam> liveServings,
            Instant now) {
        StorefrontRow row = buildRow(bundle, version, saleState, liveServings);
        return storefrontRepository.upsertBundle(Ids.newId(), row, now);
    }

    public StorefrontRow buildRow(
            Bundle bundle,
            BundleVersion version,
            SaleState saleState,
            Map<UUID, ServingExam> liveServings) {
        BundleSnapshot snap = version.getSnapshot();
        SubjectCatalog subjects = snapshotSupport.subjectCatalog();
        ExamKindRef kindRef = resolveExamKindRef(bundle.getExamKindItemId());

        DetailBenefits benefits = new DetailBenefits(
                snap.benefits().aiExplanation(),
                snap.benefits().strengthWeakness(),
                snap.benefits().practice(),
                snap.benefits().note());

        List<BundleDetailItem> detailItems = new ArrayList<>(snap.items().size());
        Set<UUID> subjectIds = new LinkedHashSet<>();
        boolean hasReviewItem = false;

        int minQuestions = 0;
        int maxQuestions = 0;
        boolean anyQuestions = false;
        int minMinutes = 0;
        int maxMinutes = 0;
        boolean anyMinutes = false;
        BigDecimal minPoints = BigDecimal.ZERO;
        BigDecimal maxPoints = BigDecimal.ZERO;
        boolean anyPoints = false;

        for (SnapshotItem snapItem : snap.items()) {
            ServingExam live = liveServings.get(snapItem.examId());
            String code = snapItem.code();
            String name = snapItem.name();
            String scope = snapItem.scope();
            String partCode = snapItem.partCode();
            var subjectName = snapItem.subjectName();
            String buildMode = snapItem.buildMode();
            int itemVersionNo = snapItem.versionNo();
            IntRange qRange = snapItem.questionCount();
            IntRange dRange = snapItem.durationMinutes();
            DecimalRange pRange = snapItem.maxPoints();

            if (live != null && snapshotSupport.isOnSale(live)) {
                PublishedVersion v = live.version();
                ExamMetrics liveMetrics = snapshotSupport.computeMetrics(live);
                SubjectInfo liveSubject = snapshotSupport.resolveSubject(v, subjects);
                code = v.code();
                name = v.name();
                scope = v.scope();
                partCode = v.partCode();
                subjectName = liveSubject.name();
                buildMode = "REVIEW".equals(v.buildMode()) ? "REVIEW" : "STANDARD";
                itemVersionNo = v.versionNo();
                qRange = liveMetrics.questionCount();
                dRange = liveMetrics.durationMinutes();
                pRange = liveMetrics.maxPoints();
                subjectIds.addAll(snapshotSupport.collectSubjectIds(live, subjects));
            } else {
                if (snapItem.subjectItemId() != null) {
                    subjectIds.add(snapItem.subjectItemId());
                } else if (snapItem.subjectCode() != null) {
                    CatalogItemView subItem = subjects.byCode().get(snapItem.subjectCode().trim().toLowerCase());
                    if (subItem != null) {
                        subjectIds.add(subItem.id());
                    }
                }
                if (live != null) {
                    subjectIds.addAll(snapshotSupport.collectSubjectIds(live, subjects));
                }
            }

            if ("REVIEW".equals(buildMode)) {
                hasReviewItem = true;
            }
            if (qRange != null) {
                minQuestions += qRange.min();
                maxQuestions += qRange.max();
                anyQuestions = true;
            }
            if (dRange != null) {
                minMinutes += dRange.min();
                maxMinutes += dRange.max();
                anyMinutes = true;
            }
            if (pRange != null) {
                minPoints = minPoints.add(pRange.min());
                maxPoints = maxPoints.add(pRange.max());
                anyPoints = true;
            }

            detailItems.add(new BundleDetailItem(
                    snapItem.examId(),
                    code,
                    name,
                    scope,
                    partCode,
                    subjectName,
                    buildMode == null ? "STANDARD" : buildMode,
                    qRange,
                    dRange,
                    pRange,
                    snap.attemptsPerExam(),
                    benefits,
                    snap.resultView(),
                    itemVersionNo));
        }

        IntRange totalQuestions = anyQuestions ? new IntRange(minQuestions, maxQuestions) : null;
        IntRange totalMinutes = anyMinutes ? new IntRange(minMinutes, maxMinutes) : null;
        DecimalRange totalPoints = anyPoints ? new DecimalRange(minPoints, maxPoints) : null;
        String bundleBuildMode = hasReviewItem ? "REVIEW" : "STANDARD";

        ProductCard card = new ProductCard(
                bundle.getCode(),
                snap.name(),
                snap.summary(),
                null,
                kindRef,
                "BUNDLE",
                null,
                bundleBuildMode,
                totalQuestions,
                totalMinutes,
                snap.attemptsPerExam(),
                snap.listPriceVnd(),
                snap.listPriceVnd(),
                null,
                version.getVersionNo(),
                "BUNDLE");

        ProductDetail detail = new ProductDetail(
                bundle.getCode(),
                snap.name(),
                snap.summary(),
                null,
                kindRef,
                "BUNDLE",
                null,
                bundleBuildMode,
                totalQuestions,
                totalMinutes,
                totalPoints,
                snap.attemptsPerExam(),
                snap.listPriceVnd(),
                snap.listPriceVnd(),
                null,
                version.getVersionNo(),
                snap.description(),
                null,
                null,
                List.of(),
                List.of(),
                new DetailScoring(null, null, null, null),
                List.of(),
                benefits,
                snap.resultView(),
                "BUNDLE",
                detailItems);

        StorefrontRow unhashed = new StorefrontRow(
                null,
                bundle.getId(),
                ProductType.BUNDLE,
                bundle.getCode(),
                BundleCodeNormalizer.normalize(bundle.getCode()),
                version.getVersionNo(),
                saleState,
                snap.name(),
                StorefrontSearchText.of(snap.name(), bundle.getCode()),
                bundle.getExamKindItemId(),
                kindRef.code(),
                "BUNDLE",
                null,
                List.copyOf(subjectIds),
                List.of(),
                List.of(),
                totalQuestions == null ? null : totalQuestions.min(),
                totalQuestions == null ? null : totalQuestions.max(),
                totalMinutes == null ? null : totalMinutes.min(),
                totalMinutes == null ? null : totalMinutes.max(),
                totalPoints == null ? null : totalPoints.min(),
                totalPoints == null ? null : totalPoints.max(),
                bundleBuildMode,
                snap.listPriceVnd(),
                snap.attemptsPerExam(),
                null,
                json(card),
                json(detail),
                "",
                version.getPublishedAt());
        return withHash(unhashed);
    }

    private ExamKindRef resolveExamKindRef(UUID examKindItemId) {
        CatalogItemView item = catalogLookup.findItem(examKindItemId).orElse(null);
        if (item == null) {
            return new ExamKindRef(examKindItemId.toString(), examKindItemId.toString(), examKindItemId.toString());
        }
        return new ExamKindRef(item.code(), item.nameVi(), item.nameEn());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize bundle storefront model", e);
        }
    }

    private StorefrontRow withHash(StorefrontRow row) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String hash = HexFormat.of().formatHex(digest.digest(json(row).getBytes(StandardCharsets.UTF_8)));
        return new StorefrontRow(
                row.examId(),
                row.bundleId(),
                row.productType(),
                row.code(),
                row.codeNormalized(),
                row.versionNo(),
                row.saleState(),
                row.name(),
                row.searchText(),
                row.examItemId(),
                row.kindCode(),
                row.scope(),
                row.scopeItemId(),
                row.subjectItemIds(),
                row.partCodes(),
                row.partNames(),
                row.questionCount(),
                row.questionCountMax(),
                row.durationMinutes(),
                row.durationMinutesMax(),
                row.maxPoints(),
                row.maxPointsMax(),
                row.buildMode(),
                row.listPriceVnd(),
                row.attemptsGranted(),
                row.coverAssetId(),
                row.card(),
                row.detail(),
                hash,
                row.publishedAt());
    }
}
