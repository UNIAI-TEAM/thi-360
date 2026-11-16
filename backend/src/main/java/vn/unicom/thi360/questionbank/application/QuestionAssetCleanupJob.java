package vn.unicom.thi360.questionbank.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Worker dọn tài nguyên mồ côi (spec 4.1, plan D/BE-2b): xóa {@code question_asset} cũ hơn
 * {@code orphanAge} (mặc định 24h) mà <b>không</b> có dòng nào trong
 * {@code question_asset_ref}; tài nguyên của phiên bản cũ luôn còn ref nên không bị đụng
 * (kể cả câu xóa mềm — quyết định lane 8.9).
 *
 * <p>Khóa các ứng viên bằng {@code FOR UPDATE SKIP LOCKED} trong cùng câu lệnh xóa.
 * Một lần lưu đang giữ khóa FK sẽ khiến asset bị bỏ qua; nếu cleanup khóa trước,
 * FK ngăn lưu ref tới asset đã xóa. Xóa object storage sau khi DB commit (plan B4).
 */
@Component
public class QuestionAssetCleanupJob {
    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private static final String DELETE_ORPHANS_SQL = """
            DELETE FROM questionbank.question_asset a
            WHERE a.id IN (
                  SELECT c.id FROM questionbank.question_asset c
                  WHERE c.created_at < ?
                    AND NOT EXISTS (
                        SELECT 1 FROM questionbank.question_asset_ref r
                        WHERE r.asset_id = c.id)
                  ORDER BY c.created_at
                  LIMIT ?
                  FOR UPDATE SKIP LOCKED)
              AND NOT EXISTS (
                  SELECT 1 FROM questionbank.question_asset_ref r
                  WHERE r.asset_id = a.id)
            RETURNING a.id, a.object_key
            """;

    private final JdbcTemplate jdbc;
    private final ObjectStorage objectStorage;
    private final QuestionAssetCleanupProperties properties;

    public QuestionAssetCleanupJob(
            JdbcTemplate jdbc,
            ObjectStorage objectStorage,
            QuestionAssetCleanupProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectStorage = Objects.requireNonNull(objectStorage);
        this.properties = Objects.requireNonNull(properties);
    }

    /** Một lần quét: trả số dòng đã xóa. Object xóa sau commit (best-effort, có log). */
    @Transactional
    public int run() {
        Instant cutoff = Instant.now().minus(properties.orphanAge());
        List<DeletedAsset> deleted = jdbc.query(
                DELETE_ORPHANS_SQL,
                (rs, row) -> new DeletedAsset(
                        rs.getObject("id", UUID.class), rs.getString("object_key")),
                java.sql.Timestamp.from(cutoff),
                Math.max(1, properties.batchSize()));
        if (deleted.isEmpty()) {
            return 0;
        }
        List<DeletedAsset> copy = List.copyOf(deleted);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (DeletedAsset asset : copy) {
                    try {
                        objectStorage.delete(asset.objectKey());
                    } catch (RuntimeException ex) {
                        jobLog.atWarn().setCause(ex)
                                .addKeyValue("assetId", asset.id())
                                .addKeyValue("objectKey", asset.objectKey())
                                .log("Orphan asset row deleted but object delete failed");
                    }
                }
                jobLog.atInfo()
                        .addKeyValue("job", "question-asset-cleanup")
                        .addKeyValue("deleted", copy.size())
                        .log("Orphan question assets cleaned");
            }
        });
        return deleted.size();
    }

    private record DeletedAsset(UUID id, String objectKey) {
    }

    /**
     * Vòng lặp định kỳ (theo convention {@code ImportBatchProcessorJob}): tách bean để
     * {@code @ConditionalOnProperty} tắt được riêng mà không ảnh hưởng {@link #run()} khi
     * test gọi trực tiếp.
     */
    @Component
    @Profile("worker")
    @ConditionalOnProperty(
            name = "thi360.questionbank.assets.cleanup.scheduling.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class Worker {

        private final QuestionAssetCleanupJob job;

        public Worker(QuestionAssetCleanupJob job) {
            this.job = job;
        }

        @Scheduled(cron = "${thi360.questionbank.assets.cleanup.cron:0 0 * * * *}", zone = "UTC")
        public void sweep() {
            job.run();
        }
    }
}
