package vn.unicom.thi360.exam.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * an unused cover stamps {@code orphaned_at}; a cover that is used again loses the stamp; one that stayed unused for
 * the whole {@code orphanAge} is deleted, row first (in one transaction) and object after commit.
 *
 * <p>Two sweeps may overlap: deletion locks its candidate rows with {@code FOR UPDATE SKIP LOCKED}, so each row is
 * deleted by exactly one of them.
 */
@Service
public class ExamCoverCleanup {
    private static final Logger log = LoggerFactory.getLogger("thi360.job");
    private static final String UNUSED = "NOT EXISTS (SELECT 1 FROM exam.exam e WHERE e.cover_asset_id = a.id) "
            + "AND NOT EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.config->>'coverAssetId' = a.id::text)";

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

    private record Removed(String objectKey) { }

    /** One sweep; returns how many covers it deleted. */
    public int run() {
        Instant now = Instant.now(clock);
        Timestamp stamp = Timestamp.from(now);
        Timestamp cutoff = Timestamp.from(now.minus(properties.orphanAge()));
        List<Removed> removed = transaction.execute(status -> {
            jdbc.update("UPDATE exam.exam_cover_asset a SET orphaned_at = ? WHERE a.orphaned_at IS NULL AND " + UNUSED, stamp);
            jdbc.update("UPDATE exam.exam_cover_asset a SET orphaned_at = NULL WHERE a.orphaned_at IS NOT NULL AND NOT (" + UNUSED + ")");
            return new ArrayList<>(jdbc.query("DELETE FROM exam.exam_cover_asset a WHERE a.id IN ("
                            + "SELECT c.id FROM exam.exam_cover_asset c WHERE c.orphaned_at <= ? "
                            + "AND NOT EXISTS (SELECT 1 FROM exam.exam e WHERE e.cover_asset_id = c.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.config->>'coverAssetId' = c.id::text) "
                            + "ORDER BY c.orphaned_at LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING a.object_key",
                    (rs, n) -> new Removed(rs.getString(1)), cutoff, properties.batchSize()));
        });
        if (removed == null) return 0;
        for (Removed cover : removed) {
            try {
                storage.delete(cover.objectKey());
            } catch (RuntimeException e) {
                log.atWarn().setCause(e).addKeyValue("objectKey", cover.objectKey()).log("Could not delete a cover object");
            }
        }
        if (!removed.isEmpty()) log.atInfo().addKeyValue("deleted", removed.size()).log("Exam cover cleanup");
        return removed.size();
    }
}
