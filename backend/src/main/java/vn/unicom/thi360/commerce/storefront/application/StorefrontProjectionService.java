package vn.unicom.thi360.commerce.storefront.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.storefront.domain.ProjectionOutcome;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Writes the storefront row of one exam from {@link PublishedExamPort#findServing} (E2, E24). The event handlers and the
 * rebuild call only this class, so both produce the same rows.
 *
 * <p>The whole read-and-write runs in a transaction of its own ({@code REQUIRES_NEW}). The relay runs a handler inside its
 * own transaction and records a failure there: a failure inside this transaction rolls back only this one, so the relay
 * can still write the attempt and its retry schedule (plan B2, AC-12).
 */
@Service
public class StorefrontProjectionService {

    private static final Logger log = LoggerFactory.getLogger(StorefrontProjectionService.class);

    private final PublishedExamPort port;
    private final StorefrontProjector projector;
    private final StorefrontProductRepository repository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public StorefrontProjectionService(PublishedExamPort port, StorefrontProjector projector,
            StorefrontProductRepository repository, PlatformTransactionManager transactionManager, Clock clock) {
        this.port = port;
        this.projector = projector;
        this.repository = repository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNew = template;
        this.clock = clock;
    }

    /**
     * Re-reads the exam through the port and stores its row. Idempotent: the same state gives the same row, a replay
     * changes nothing, and a version older than the stored one never overwrites it. A failure propagates (the relay
     * retries); nothing is partly written.
     */
    public ProjectionOutcome project(UUID examId) {
        return requiresNew.execute(status -> projectInTransaction(examId));
    }

    private ProjectionOutcome projectInTransaction(UUID examId) {
        Optional<ServingExam> serving = port.findServing(examId);
        if (serving.isEmpty()) {
            return ProjectionOutcome.NOT_SERVED;
        }
        Optional<StorefrontRow> row = projector.build(serving.get());
        if (row.isEmpty()) {
            log.atWarn().addKeyValue("examId", examId)
                    .log("Exam has no listing price or attempts in its serving version; no storefront row made");
            return ProjectionOutcome.NOT_SELLABLE;
        }
        boolean written = repository.upsert(Ids.newId(), row.get(), Instant.now(clock));
        return written ? ProjectionOutcome.WRITTEN : ProjectionOutcome.UNCHANGED;
    }
}
