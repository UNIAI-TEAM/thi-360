package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.application.QuestionImportCleanupJob;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileType;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUpload;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUploadRepository;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class QuestionImportCleanupIT extends AbstractIntegrationTest {
    @Autowired private QuestionImportUploadRepository uploads;
    @Autowired private ObjectStorage storage;
    @Autowired private TransactionTemplate transactions;
    @Autowired private Clock clock;

    @Test
    void cleanupDeletesExpiredUploadsButKeepsFreshOnes() {
        Instant now = clock.instant();
        UUID expired = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID committed = UUID.randomUUID();
        String expiredKey = "question-imports/" + expired;
        String freshKey = "question-imports/" + fresh;
        String committedKey = "question-imports/" + committed;
        storage.put(expiredKey, new byte[] {1}, "application/octet-stream");
        storage.put(freshKey, new byte[] {2}, "application/octet-stream");
        storage.put(committedKey, new byte[] {3}, "application/octet-stream");
        uploads.saveAndFlush(new QuestionImportUpload(expired, expiredKey, "expired.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401)));
        uploads.saveAndFlush(new QuestionImportUpload(fresh, freshKey, "fresh.csv", ImportFileType.CSV,
                null, 1, List.of(), now.plusSeconds(60), UUID.randomUUID(), now));
        var completed = new QuestionImportUpload(committed, committedKey, "done.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401));
        completed.markCommitted(null, now.minusSeconds(2));
        uploads.saveAndFlush(completed);

        new QuestionImportCleanupJob(uploads, storage, transactions, clock).cleanup();

        assertThat(uploads.findById(expired)).isEmpty();
        assertThat(storage.exists(expiredKey)).isFalse();
        assertThat(uploads.findById(fresh)).isPresent();
        assertThat(storage.exists(freshKey)).isTrue();
        assertThat(uploads.findById(committed)).isEmpty();
        assertThat(storage.exists(committedKey)).isFalse();
    }
}
