package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.PublishedExamPort;

/**
 * R1-9: {@code findServing} reports the exam status and the serving versions from one snapshot. A discontinue changes
 * both in one transaction; a reader must never see the outer status of one moment and the version of another. Runs on
 * committed rows with a writer that flips the pair back and forth while readers read.
 */
class PublishedExamSnapshotConsistencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PublishedExamPort port;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void statusAndVersionAlwaysComeFromTheSameMoment() throws Exception {
        UUID kind = jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id "
                + "WHERE g.code = 'EXAM' AND i.code = 'HSA'", UUID.class);
        UUID exam = UUID.randomUUID();
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, status, serving_version) "
                    + "VALUES (?, ?, ?, 'Snapshot test', ?, 'FULL', 'SHARED', 'PUBLISHED', 1)", exam, exam.toString(), exam.toString(), kind);
            var paths = jdbc.execute((java.sql.Connection connection) -> connection.createArrayOf("text", new String[] {"default"}));
            jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, "
                    + "check_run_id, state, status, published_at, published_by) VALUES (?, ?, 1, ?::jsonb, '{\"parts\":[]}'::jsonb, ?, ?, ?, "
                    + "'SERVING', 'PUBLISHED', ?, ?)", UUID.randomUUID(), exam,
                    "{\"code\":\"S\",\"name\":\"Snapshot test\",\"kindItemId\":\"" + kind + "\",\"kindCode\":\"HSA\"}", paths, paths,
                    UUID.randomUUID(), Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")), UUID.randomUUID());
        });
        AtomicBoolean done = new AtomicBoolean();
        try {
            CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < 300 && !done.get(); i++) {
                        boolean discontinue = i % 2 == 0;
                        tx.executeWithoutResult(status -> {
                            jdbc.update("UPDATE exam.exam SET status = ? WHERE id = ?", discontinue ? "DISCONTINUED" : "PUBLISHED", exam);
                            if (discontinue) {
                                jdbc.update("UPDATE exam.exam_version SET status = 'DISCONTINUED', discontinued_at = now(), "
                                        + "discontinued_by = ? WHERE exam_id = ?", UUID.randomUUID(), exam);
                            } else {
                                jdbc.update("UPDATE exam.exam_version SET status = 'PUBLISHED', discontinued_at = NULL, "
                                        + "discontinued_by = NULL WHERE exam_id = ?", exam);
                            }
                        });
                    }
                } finally {
                    // Whatever happens to the writer, the reader loop below must end.
                    done.set(true);
                }
            });
            long reads = 0;
            while (!done.get()) {
                var serving = port.findServing(exam).orElseThrow();
                assertThat(serving.version().status()).as("outer status and version status of one answer").isEqualTo(serving.status());
                reads++;
            }
            writer.get(60, TimeUnit.SECONDS);
            assertThat(reads).isPositive();
        } finally {
            done.set(true);
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", exam);
                jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
            });
        }
    }
}
