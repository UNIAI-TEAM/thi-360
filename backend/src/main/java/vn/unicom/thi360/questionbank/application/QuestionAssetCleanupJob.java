package vn.unicom.thi360.questionbank.application;

import java.time.Instant;
import java.util.ArrayList;
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

    private static final String SELECT_PENDING_SQL = """
            SELECT object_key, asset_id
            FROM questionbank.question_asset_pending_delete
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String INSERT_INTENT_SQL = """
            INSERT INTO questionbank.question_asset_pending_delete (object_key, asset_id)
            VALUES (?, ?)
            ON CONFLICT (object_key) DO NOTHING
            """;

    private static final String DELETE_INTENT_SQL =
            "DELETE FROM questionbank.question_asset_pending_delete WHERE object_key = ?";


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

    /**
     * Một lần quét: thử lại các ý định xóa object còn dư trước (R1-3 — durable delete
     * intent), rồi xóa dòng orphan mới. Transaction ghi ý định cùng lúc xóa dòng; object
     * chỉ xóa sau khi commit, xóa xong mới gỡ dòng ý định. Trả số dòng xóa trong lần này.
     */
    @Transactional
    public int run() {
        Instant cutoff = Instant.now().minus(properties.orphanAge());
        List<PendingDelete> pending = jdbc.query(
                SELECT_PENDING_SQL,
                (rs, row) -> new PendingDelete(
                        rs.getString("object_key"), rs.getObject("asset_id", UUID.class)),
                Math.max(1, properties.batchSize()));
        List<PendingDelete> deleted = jdbc.query(
                DELETE_ORPHANS_SQL,
                (rs, row) -> new PendingDelete(
                        rs.getString("object_key"), rs.getObject("id", UUID.class)),
                java.sql.Timestamp.from(cutoff),
                Math.max(1, properties.batchSize()));
        for (PendingDelete asset : deleted) {
            jdbc.update(INSERT_INTENT_SQL, asset.objectKey(), asset.assetId());
        }
        if (pending.isEmpty() && deleted.isEmpty()) {
            return 0;
        }
        List<PendingDelete> toDelete = new ArrayList<>(pending);
        toDelete.addAll(deleted);
        int deletedCount = deleted.size();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int objectsRemoved = 0;
                for (PendingDelete asset : toDelete) {
                    try {
                        objectStorage.delete(asset.objectKey());
                        // Object đã xóa mới gỡ ý định; lỗi giữ lại ý định để quét sau thử lại.
                        jdbc.update(DELETE_INTENT_SQL, asset.objectKey());
                        objectsRemoved++;
                    } catch (RuntimeException ex) {
                        jobLog.atWarn().setCause(ex)
                                .addKeyValue("assetId", asset.assetId())
                                .addKeyValue("objectKey", asset.objectKey())
                                .log("Orphan asset object delete failed; intent kept for next sweep");
                    }
                }
                jobLog.atInfo()
                        .addKeyValue("job", "question-asset-cleanup")
                        .addKeyValue("rows", deletedCount)
                        .addKeyValue("objects", objectsRemoved)
                        .log("Orphan question assets cleaned");
            }
        });
        return deleted.size();
    }

    private record PendingDelete(String objectKey, UUID assetId) {
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
