package vn.unicom.thi360.questionbank.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUpload;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUploadRepository;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** Removes expired uploads without racing a commit holding the upload row lock. */
@Component
@ConditionalOnProperty(name = "thi360.questionbank.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class QuestionImportCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(QuestionImportCleanupJob.class);
    private final QuestionImportUploadRepository uploads;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public QuestionImportCleanupJob(QuestionImportUploadRepository uploads, ObjectStorage storage,
            TransactionTemplate transactions, Clock clock) {
        this.uploads = uploads;
        this.storage = storage;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${thi360.questionbank.import.cleanup-delay:PT1H}")
    public void cleanup() {
        Instant now = clock.instant();
        var candidates = uploads.findByStatusInAndExpiresAtBefore(
                List.of(QuestionImportUpload.Status.UPLOADED, QuestionImportUpload.Status.COMMITTED), now);
        for (var candidate : candidates) {
            String key = transactions.execute(status -> uploads.findByIdForUpdate(candidate.getId())
                    .filter(row -> row.getStatus() != QuestionImportUpload.Status.COMMITTING
                            && !row.getExpiresAt().isAfter(now))
                    .map(row -> {
                        uploads.delete(row);
                        return row.getObjectKey();
                    }).orElse(null));
            if (key != null) {
                try {
                    storage.delete(key);
                } catch (RuntimeException e) {
                    log.warn("Could not delete expired import source object {}", key, e);
                }
            }
        }
    }
}
