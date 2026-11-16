package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;

class ExamCheckResultStoreIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamCheckResultStore results;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentRevisionChangePreventsSavingStaleResult() throws Exception {
        UUID examId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                        + "VALUES (?, ?, ?, 'Stale check test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        var empty = mapper.readTree("{}");
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L,
                "fingerprint", empty);
        try {
            assertThat(results.save(input, "VALID", 0, Instant.now(), 1, mapper.readTree("{\"value\":1}"), empty))
                    .isTrue();
            CountDownLatch updated = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var structureSave = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.update("UPDATE exam.exam SET revision = revision + 1 WHERE id = ?", examId);
                    updated.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return null;
                }));
                assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
                var staleSave = executor.submit(() -> results.save(input, "INVALID", 1, Instant.now(), 2,
                        mapper.readTree("{\"value\":2}"), empty));
                release.countDown();
                structureSave.get(20, TimeUnit.SECONDS);
                assertThat(staleSave.get(20, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(jdbc.queryForObject("SELECT result->>'value' FROM exam.exam_check_run WHERE exam_id = ?",
                    String.class, examId)).isEqualTo("1");
            assertThat(jdbc.queryForObject("SELECT check_status FROM exam.exam WHERE id = ?", String.class, examId))
                    .isEqualTo("VALID");
        } finally {
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
        }
    }
}
