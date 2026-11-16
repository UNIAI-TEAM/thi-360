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
        UUID inFlight = UUID.randomUUID();
        String expiredKey = "question-imports/" + expired;
        String freshKey = "question-imports/" + fresh;
        String committedKey = "question-imports/" + committed;
        String inFlightKey = "question-imports/" + inFlight;
        storage.put(expiredKey, new byte[] {1}, "application/octet-stream");
        storage.put(freshKey, new byte[] {2}, "application/octet-stream");
        storage.put(committedKey, new byte[] {3}, "application/octet-stream");
        storage.put(inFlightKey, new byte[] {4}, "application/octet-stream");
        uploads.saveAndFlush(new QuestionImportUpload(expired, expiredKey, "expired.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401)));
        uploads.saveAndFlush(new QuestionImportUpload(fresh, freshKey, "fresh.csv", ImportFileType.CSV,
                null, 1, List.of(), now.plusSeconds(60), UUID.randomUUID(), now));
        var completed = new QuestionImportUpload(committed, committedKey, "done.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401));
        completed.markCommitted(null, now.minusSeconds(2));
        uploads.saveAndFlush(completed);
        var committing = new QuestionImportUpload(inFlight, inFlightKey, "in-flight.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401));
        committing.markCommitting();
        uploads.saveAndFlush(committing);

        new QuestionImportCleanupJob(uploads, storage, transactions, clock).cleanup();

        assertThat(uploads.findById(expired)).isEmpty();
        assertThat(storage.exists(expiredKey)).isFalse();
        assertThat(uploads.findById(fresh)).isPresent();
        assertThat(storage.exists(freshKey)).isTrue();
        assertThat(uploads.findById(committed)).isEmpty();
        assertThat(storage.exists(committedKey)).isFalse();
        assertThat(uploads.findById(inFlight)).isPresent();
        assertThat(storage.exists(inFlightKey)).isTrue();
    }

    @Test
    void failedObjectDeleteKeepsExpiredRowUntilRetrySucceeds() {
        // Xóa object lỗi (MinIO lỗi thoáng): dòng hết hạn vẫn còn để lượt quét sau xóa tiếp —
        // metadata chỉ đi khi object đã xóa xong (R1-7).
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String key = "question-imports/" + id;
        storage.put(key, new byte[] {1}, "application/octet-stream");
        uploads.saveAndFlush(new QuestionImportUpload(id, key, "expired.csv", ImportFileType.CSV,
                null, 1, List.of(), now.minusSeconds(1), UUID.randomUUID(), now.minusSeconds(86401)));

        var flaky = new FailingOnceStorage(storage, key);
        var job = new QuestionImportCleanupJob(uploads, flaky, transactions, clock);
        job.cleanup();
        assertThat(uploads.findById(id)).isPresent();
        assertThat(storage.exists(key)).isTrue();

        job.cleanup();
        assertThat(uploads.findById(id)).isEmpty();
        assertThat(storage.exists(key)).isFalse();
    }

    private static final class FailingOnceStorage implements ObjectStorage {
        private final ObjectStorage delegate;
        private final String targetKey;
        private boolean failed;

        FailingOnceStorage(ObjectStorage delegate, String targetKey) {
            this.delegate = delegate;
            this.targetKey = targetKey;
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            delegate.put(key, content, contentType);
        }

        @Override
        public java.util.Optional<byte[]> get(String key) {
            return delegate.get(key);
        }

        @Override
        public void delete(String key) {
            if (!failed && key.equals(targetKey)) {
                failed = true;
                throw new RuntimeException("minio down");
            }
            delegate.delete(key);
        }

        @Override
        public boolean exists(String key) {
            return delegate.exists(key);
        }
    }
}
