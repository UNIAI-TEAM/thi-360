package vn.unicom.thi360.commerce.storefront.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.shared.ids.Ids;

/** Projects frozen serving versions. Relay writes are isolated; rebuild writes join the audited transaction. */
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
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Relay reads and writes in a separate transaction so a failure cannot poison the relay transaction. */
    public ProjectionOutcome project(UUID examId) {
        return requiresNew.execute(status -> write(read(examId)));
    }

    /** Rebuild reads in isolation, then writes behind a JDBC savepoint in the caller's audited transaction. */
    public ProjectionOutcome projectInSavepoint(UUID examId) {
        ReadResult result = requiresNew.execute(status -> read(examId));
        return repository.inSavepoint(() -> write(result));
    }

    private record ReadResult(StorefrontRow row, ProjectionOutcome emptyOutcome) { }

    private ReadResult read(UUID examId) {
        Optional<ServingExam> serving = port.findServing(examId);
        if (serving.isEmpty()) return new ReadResult(null, ProjectionOutcome.NOT_SERVED);
        Optional<StorefrontRow> row = projector.build(serving.get(), servingVersions(serving.get()));
        if (row.isEmpty()) {
            log.atWarn().addKeyValue("examId", examId)
                    .log("Exam has no listing price or attempts in its serving version; no storefront row made");
            return new ReadResult(null, ProjectionOutcome.NOT_SELLABLE);
        }
        return new ReadResult(row.get(), null);
    }

    private ProjectionOutcome write(ReadResult result) {
        if (result.row() == null) return result.emptyOutcome();
        return repository.upsert(Ids.newId(), result.row(), Instant.now(clock))
                ? ProjectionOutcome.WRITTEN : ProjectionOutcome.UNCHANGED;
    }

    private Map<Integer, PublishedVersion> servingVersions(ServingExam serving) {
        Map<Integer, PublishedVersion> versions = new LinkedHashMap<>();
        versions.put(serving.version().versionNo(), serving.version());
        for (ServingPath path : serving.servingPaths()) {
            if (!versions.containsKey(path.versionNo())) {
                versions.put(path.versionNo(), port.loadVersion(serving.examId(), path.versionNo()).orElseThrow(
                        () -> new IllegalStateException("Serving version " + path.versionNo() + " of exam "
                                + serving.examId() + " is not readable")));
            }
        }
        return versions;
    }
}
