package vn.unicom.thi360.commerce.storefront.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.storefront.api.StorefrontRebuildResponse;
import vn.unicom.thi360.commerce.storefront.domain.ProjectionOutcome;
import vn.unicom.thi360.exam.spi.PublishedExamPort;

/**
 * {@code POST /api/v1/admin/storefront/rebuild} (TM 10.1): builds the row of every exam the port lists as published or
 * discontinued, the repair for events that were lost or failed. It makes no row for a draft, and running it again changes
 * nothing. One exam that fails does not stop the others; it is counted and logged.
 */
@Service
public class StorefrontRebuildService {

    public static final String AUDIT_ACTION = "STOREFRONT_REBUILT";

    private static final Logger log = LoggerFactory.getLogger(StorefrontRebuildService.class);

    private final PublishedExamPort port;
    private final StorefrontProjectionService projection;
    private final AuditRecorder audit;

    public StorefrontRebuildService(PublishedExamPort port, StorefrontProjectionService projection,
            AuditRecorder audit) {
        this.port = port;
        this.projection = projection;
        this.audit = audit;
    }

    /** The transaction only carries the audit event (the outbox demands one); each row is written in its own. */
    @Transactional
    public StorefrontRebuildResponse rebuild() {
        List<UUID> examIds = port.listPublishedExamIds();
        int written = 0;
        int unchanged = 0;
        int notSellable = 0;
        int failed = 0;
        for (UUID examId : examIds) {
            try {
                ProjectionOutcome outcome = projection.project(examId);
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
        StorefrontRebuildResponse result = new StorefrontRebuildResponse(examIds.size(), written, unchanged,
                notSellable, failed);
        audit.record(AuditEvent.success("COMMERCE", AUDIT_ACTION, "storefront", "storefront_product", null,
                Map.of("examined", result.examined(), "written", result.written(), "unchanged", result.unchanged(),
                        "notSellable", result.notSellable(), "failed", result.failed())));
        return result;
    }
}
