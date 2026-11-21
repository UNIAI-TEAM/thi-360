package vn.unicom.thi360.commerce.bundle.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.SnapshotItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleVersionRepository;
import vn.unicom.thi360.commerce.storefront.api.BundleDetailItem;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;

/**
 * Updates published bundle storefront rows when a component exam publishes a new version or is discontinued
 * (TM 16.2, AC-12, AC-13, AC-30). Isolates each affected bundle in its own {@code REQUIRES_NEW} transaction so a
 * failure on one bundle neither blocks healthy sibling bundles nor poisons {@code OutboxRelay}.
 */
@Service
public class BundleEventService {

    private static final Logger log = LoggerFactory.getLogger(BundleEventService.class);

    private final BundleRepository bundleRepository;
    private final BundleVersionRepository bundleVersionRepository;
    private final StorefrontProductRepository storefrontRepository;
    private final PublishedExamPort publishedExamPort;
    private final BundleExamSnapshotSupport snapshotSupport;
    private final BundleStorefrontProjector storefrontProjector;
    private final TransactionTemplate requiresNewTx;
    private final TransactionTemplate readOnlyRequiresNewTx;
    private final ObjectMapper mapper;
    private final Clock clock;

    public BundleEventService(
            BundleRepository bundleRepository,
            BundleVersionRepository bundleVersionRepository,
            StorefrontProductRepository storefrontRepository,
            PublishedExamPort publishedExamPort,
            BundleExamSnapshotSupport snapshotSupport,
            BundleStorefrontProjector storefrontProjector,
            PlatformTransactionManager transactionManager,
            ObjectMapper mapper,
            Clock clock) {
        this.bundleRepository = bundleRepository;
        this.bundleVersionRepository = bundleVersionRepository;
        this.storefrontRepository = storefrontRepository;
        this.publishedExamPort = publishedExamPort;
        this.snapshotSupport = snapshotSupport;
        this.storefrontProjector = storefrontProjector;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnlyRequiresNewTx = new TransactionTemplate(transactionManager);
        this.readOnlyRequiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnlyRequiresNewTx.setReadOnly(true);
        this.mapper = mapper;
        this.clock = clock;
    }

    public void onExamEvent(String topic, UUID examId, Integer versionNo) {
        if (examId == null) {
            return;
        }
        List<UUID> bundleIds = readOnlyRequiresNewTx.execute(
                status -> bundleVersionRepository.findActivePublishedBundleIdsContainingExam(examId));
        if (bundleIds == null || bundleIds.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        RuntimeException firstFailure = null;
        for (UUID bundleId : bundleIds) {
            try {
                requiresNewTx.executeWithoutResult(
                        status -> reprojectBundle(bundleId, topic, examId, versionNo, now));
            } catch (RuntimeException ex) {
                log.atError().setCause(ex)
                        .addKeyValue("bundleId", bundleId)
                        .addKeyValue("examId", examId)
                        .addKeyValue("topic", topic)
                        .log("Failed to reproject bundle on exam event");
                if (firstFailure == null) {
                    firstFailure = ex;
                } else {
                    firstFailure.addSuppressed(ex);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void reprojectBundle(UUID bundleId, String topic, UUID triggerExamId, Integer eventVersionNo, Instant now) {
        Bundle bundle = bundleRepository.lockById(bundleId).orElse(null);
        if (bundle == null || bundle.getStatus() != BundleStatus.PUBLISHED || bundle.getServingVersion() == null) {
            return;
        }
        BundleVersion version = bundleVersionRepository
                .findByBundleIdAndVersionNo(bundle.getId(), bundle.getServingVersion())
                .orElse(null);
        if (version == null) {
            return;
        }

        // Load every component exam via port first so port failures abort the REQUIRES_NEW tx cleanly for relay retry.
        Map<UUID, ServingExam> liveServings = new LinkedHashMap<>();
        for (SnapshotItem snapItem : version.getSnapshot().items()) {
            publishedExamPort.findServing(snapItem.examId())
                    .ifPresent(serving -> liveServings.put(snapItem.examId(), serving));
        }

        // Guard against out-of-order / stale events downgrading a component already updated to a newer version.
        if (eventVersionNo != null) {
            Integer existingItemVersion = currentItemVersion(bundleId, triggerExamId);
            if (existingItemVersion != null && existingItemVersion > eventVersionNo) {
                if (ExamEventTopics.DISCONTINUED.equals(topic)) {
                    return;
                }
                ServingExam triggerLive = liveServings.get(triggerExamId);
                int liveVer = (triggerLive != null && triggerLive.version() != null)
                        ? triggerLive.version().versionNo() : 0;
                if (liveVer <= existingItemVersion) {
                    return;
                }
            }
        }

        boolean allOnSale = version.getSnapshot().items().stream()
                .allMatch(item -> snapshotSupport.isOnSale(liveServings.get(item.examId())));
        SaleState saleState = allOnSale ? SaleState.ON_SALE : SaleState.UNAVAILABLE;
        storefrontProjector.project(bundle, version, saleState, liveServings, now);
    }

    private Integer currentItemVersion(UUID bundleId, UUID examId) {
        StoredProduct stored = storefrontRepository.findByBundleId(bundleId).orElse(null);
        if (stored == null || stored.detail() == null) {
            return null;
        }
        try {
            ProductDetail detail = mapper.readValue(stored.detail(), ProductDetail.class);
            for (BundleDetailItem item : detail.items()) {
                if (examId.equals(item.examId())) {
                    return item.versionNo();
                }
            }
            return null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
