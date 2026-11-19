package vn.unicom.thi360.exam.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.exam.infrastructure.ExamCoverCleanupProperties;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Removes cover images that are no longer a cover (TM 6.2, plan B5). A cover is in use while it is the cover of any
 * exam's working copy or of any published version (retired versions and copies included). The first sweep that finds
 * an unused cover stamps {@code orphaned_at}; a cover that is used again loses the stamp (also when it is attached
 * between two sweeps: {@link ExamListingService} clears it); one that stayed unused for the whole {@code orphanAge}
 * is deleted.
 *
 * <p>Deletion is durable and never holds a row lock while the object store is called: a short transaction stamps
 * {@code delete_requested_at} and takes a lease ({@code delete_claimed_until}) on the rows it will remove, using
 * {@code FOR UPDATE SKIP LOCKED} so overlapping sweeps claim different rows. A row marked for deletion can no longer
 * be attached to an exam. The object is then deleted, and only after that the row. If the object delete fails the
 * lease is released and the row stays marked, so the next sweep takes it again; a crash is covered by the lease running
 * out.
 */
@Service
public class ExamCoverCleanup {
    private static final Logger log = LoggerFactory.getLogger("thi360.job");
    private static final String UNUSED = "NOT EXISTS (SELECT 1 FROM exam.exam e WHERE e.cover_asset_id = a.id) "
            + "AND NOT EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.config->>'coverAssetId' = a.id::text)";
    private static final String CLAIMABLE = "(c.delete_claimed_until IS NULL OR c.delete_claimed_until <= ?) AND ("
            + "c.delete_requested_at IS NOT NULL OR (c.orphaned_at <= ? "
            + "AND NOT EXISTS (SELECT 1 FROM exam.exam e WHERE e.cover_asset_id = c.id) "
            + "AND NOT EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.config->>'coverAssetId' = c.id::text)))";

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final ExamCoverCleanupProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ExamCoverCleanup(JdbcTemplate jdbc, ObjectStorage storage, ExamCoverCleanupProperties properties,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    private record Claimed(UUID id, String objectKey) { }

    /** One sweep; returns how many covers it deleted (row and object). */
    public int run() {
        Instant now = Instant.now(clock);
        Timestamp stamp = Timestamp.from(now);
        Timestamp cutoff = Timestamp.from(now.minus(properties.orphanAge()));
        Timestamp lease = Timestamp.from(now.plus(properties.deleteLease()));
        List<Claimed> claimed = transaction.execute(status -> {
            jdbc.update("UPDATE exam.exam_cover_asset a SET orphaned_at = ? "
                    + "WHERE a.orphaned_at IS NULL AND a.delete_requested_at IS NULL AND " + UNUSED, stamp);
            jdbc.update("UPDATE exam.exam_cover_asset a SET orphaned_at = NULL "
                    + "WHERE a.orphaned_at IS NOT NULL AND a.delete_requested_at IS NULL AND NOT (" + UNUSED + ")");
            return jdbc.query("UPDATE exam.exam_cover_asset a SET delete_requested_at = COALESCE(a.delete_requested_at, ?), "
                            + "delete_claimed_until = ? WHERE a.id IN (SELECT c.id FROM exam.exam_cover_asset c WHERE " + CLAIMABLE
                            + " ORDER BY COALESCE(c.delete_requested_at, c.orphaned_at) LIMIT ? FOR UPDATE SKIP LOCKED) "
                            + "RETURNING a.id, a.object_key",
                    (rs, n) -> new Claimed(rs.getObject(1, UUID.class), rs.getString(2)),
                    stamp, lease, stamp, cutoff, properties.batchSize());
        });
        if (claimed == null) return 0;
        int deleted = 0;
        for (Claimed cover : claimed) {
            try {
                storage.delete(cover.objectKey());
            } catch (RuntimeException e) {
                log.atWarn().setCause(e).addKeyValue("objectKey", cover.objectKey()).log("Could not delete a cover object, retried by the next sweep");
                release(cover);
                continue;
            }
            jdbc.update("DELETE FROM exam.exam_cover_asset WHERE id = ? AND delete_requested_at IS NOT NULL", cover.id());
            deleted++;
        }
        if (deleted > 0) log.atInfo().addKeyValue("deleted", deleted).log("Exam cover cleanup");
        return deleted;
    }

    /** Gives the claim back so the next sweep need not wait for the lease; the lease still covers a failure here. */
    private void release(Claimed cover) {
        try {
            jdbc.update("UPDATE exam.exam_cover_asset SET delete_claimed_until = NULL WHERE id = ?", cover.id());
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).addKeyValue("objectKey", cover.objectKey()).log("Could not release a cover claim");
        }
    }
}
