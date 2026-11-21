package vn.unicom.thi360.commerce.storefront.application;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.bundle.application.BundleExamSnapshotSupport;
import vn.unicom.thi360.commerce.bundle.application.BundleStorefrontProjector;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.SnapshotItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleVersionRepository;
import vn.unicom.thi360.commerce.storefront.api.StorefrontRebuildResponse;
import vn.unicom.thi360.commerce.storefront.domain.ProjectionOutcome;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * {@code POST /api/v1/admin/storefront/rebuild} (TM 10.1, 16.2): builds the row of every exam and bundle that is
 * published or discontinued, the repair for events that were lost or failed. Makes no row for a draft, and running
 * again changes nothing. One item that fails does not stop the others; it is counted and logged.
 */
@Service
public class StorefrontRebuildService {

    public static final String AUDIT_ACTION = "STOREFRONT_REBUILT";

    private static final Logger log = LoggerFactory.getLogger(StorefrontRebuildService.class);

    private final PublishedExamPort port;
    private final StorefrontProjectionService projection;
    private final BundleRepository bundleRepository;
    private final BundleVersionRepository bundleVersionRepository;
    private final BundleExamSnapshotSupport snapshotSupport;
    private final BundleStorefrontProjector bundleProjector;
    private final StorefrontProductRepository storefrontRepository;
    private final TransactionTemplate requiresNew;
    private final AuditRecorder audit;
    private final Clock clock;

    public StorefrontRebuildService(
            PublishedExamPort port,
            StorefrontProjectionService projection,
            BundleRepository bundleRepository,
            BundleVersionRepository bundleVersionRepository,
            BundleExamSnapshotSupport snapshotSupport,
            BundleStorefrontProjector bundleProjector,
            StorefrontProductRepository storefrontRepository,
            PlatformTransactionManager transactionManager,
            AuditRecorder audit,
            Clock clock) {
        this.port = port;
        this.projection = projection;
        this.bundleRepository = bundleRepository;
        this.bundleVersionRepository = bundleVersionRepository;
        this.snapshotSupport = snapshotSupport;
        this.bundleProjector = bundleProjector;
        this.storefrontRepository = storefrontRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * One transaction carries every row write and the audit event (R1-6): if the audit cannot be recorded or the
     * transaction cannot commit, no row stays modified without its audit. Each product is projected behind a savepoint,
     * so one that fails is undone alone, counted and logged, and the others still commit with the audit.
     */
    @Transactional
    public StorefrontRebuildResponse rebuild() {
        List<UUID> examIds = port.listPublishedExamIds();
        List<Bundle> bundles = bundleRepository.findByStatusInOrderByCodeNormalizedAsc(
                List.of(BundleStatus.PUBLISHED, BundleStatus.DISCONTINUED));
        int written = 0;
        int unchanged = 0;
        int notSellable = 0;
        int failed = 0;
        for (UUID examId : examIds) {
            try {
                ProjectionOutcome outcome = projection.projectInSavepoint(examId);
                switch (outcome) {
                    case WRITTEN -> written++;
                    case UNCHANGED, NOT_SERVED -> unchanged++;
                    case NOT_SELLABLE -> notSellable++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.atError().setCause(e).addKeyValue("examId", examId).log("Storefront rebuild failed for an exam");
            }
        }
        Instant now = Instant.now(clock);
        for (Bundle bundle : bundles) {
            try {
                ProjectionOutcome outcome = projectBundleInSavepoint(bundle, now);
                switch (outcome) {
                    case WRITTEN -> written++;
                    case UNCHANGED, NOT_SERVED -> unchanged++;
                    case NOT_SELLABLE -> notSellable++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.atError().setCause(e).addKeyValue("bundleId", bundle.getId())
                        .log("Storefront rebuild failed for a bundle");
            }
        }
        StorefrontRebuildResponse result = new StorefrontRebuildResponse(
                examIds.size() + bundles.size(), written, unchanged, notSellable, failed);
        audit.record(AuditEvent.success("COMMERCE", AUDIT_ACTION, "storefront", "storefront_product", null,
                Map.of("examined", result.examined(), "written", result.written(), "unchanged", result.unchanged(),
                        "notSellable", result.notSellable(), "failed", result.failed())));
        return result;
    }

    private ProjectionOutcome projectBundleInSavepoint(Bundle bundle, Instant now) {
        BundleReadResult read = requiresNew.execute(status -> readBundle(bundle));
        return storefrontRepository.inSavepoint(() -> {
            if (read == null || read.row() == null) {
                return read == null ? ProjectionOutcome.NOT_SELLABLE : read.emptyOutcome();
            }
            return storefrontRepository.upsertBundle(Ids.newId(), read.row(), now)
                    ? ProjectionOutcome.WRITTEN : ProjectionOutcome.UNCHANGED;
        });
    }

    private BundleReadResult readBundle(Bundle bundle) {
        if (bundle.getServingVersion() == null) {
            return new BundleReadResult(null, ProjectionOutcome.NOT_SELLABLE);
        }
        BundleVersion version = bundleVersionRepository
                .findByBundleIdAndVersionNo(bundle.getId(), bundle.getServingVersion())
                .orElse(null);
        if (version == null) {
            return new BundleReadResult(null, ProjectionOutcome.NOT_SELLABLE);
        }
        Map<UUID, ServingExam> liveServings = new LinkedHashMap<>();
        for (SnapshotItem item : version.getSnapshot().items()) {
            port.findServing(item.examId()).ifPresent(s -> liveServings.put(item.examId(), s));
        }
        SaleState saleState;
        if (bundle.getStatus() == BundleStatus.DISCONTINUED) {
            saleState = SaleState.DISCONTINUED;
        } else {
            boolean allOnSale = version.getSnapshot().items().stream()
                    .allMatch(item -> snapshotSupport.isOnSale(liveServings.get(item.examId())));
            saleState = allOnSale ? SaleState.ON_SALE : SaleState.UNAVAILABLE;
        }
        return new BundleReadResult(bundleProjector.buildRow(bundle, version, saleState, liveServings), null);
    }

    private record BundleReadResult(StorefrontRow row, ProjectionOutcome emptyOutcome) { }
}
