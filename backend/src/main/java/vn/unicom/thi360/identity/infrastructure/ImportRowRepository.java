package vn.unicom.thi360.identity.infrastructure;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowStatus;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    List<ImportRow> findByBatchIdOrderByRowNoAsc(UUID batchId);

    List<ImportRow> findByBatchIdAndStatusOrderByRowNoAsc(UUID batchId, ImportRowStatus status);

    List<ImportRow> findByBatchIdAndStatusInOrderByRowNoAsc(UUID batchId, Collection<ImportRowStatus> statuses);

    @Query("""
            SELECT r FROM ImportRow r
            WHERE r.batchId = :batchId
              AND (r.status = vn.unicom.thi360.identity.domain.ImportRowStatus.VALID
                   OR (r.status = vn.unicom.thi360.identity.domain.ImportRowStatus.FAILED
                       AND r.retryable = true
                       AND r.attemptCount < :maxAttempts))
            ORDER BY r.rowNo ASC
            """)
    List<ImportRow> findUnfinishedRowsForProcessing(
            @Param("batchId") UUID batchId,
            @Param("maxAttempts") int maxAttempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ImportRow r WHERE r.id = :id")
    Optional<ImportRow> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ImportRow r SET r.provisionalUserId = :provisionalUserId WHERE r.id = :rowId")
    int updateProvisionalUserId(
            @Param("rowId") UUID rowId,
            @Param("provisionalUserId") UUID provisionalUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ImportRow r SET r.generatedPasswordEnc = :generatedPasswordEnc WHERE r.id = :rowId")
    int updateGeneratedPasswordEnc(
            @Param("rowId") UUID rowId,
            @Param("generatedPasswordEnc") String generatedPasswordEnc);

    Page<ImportRow> findByBatchId(UUID batchId, Pageable pageable);

    Page<ImportRow> findByBatchIdAndStatus(UUID batchId, ImportRowStatus status, Pageable pageable);

    boolean existsByBatchIdAndClassCodeNormalizedIsNotNull(UUID batchId);

    int countByBatchIdAndStatus(UUID batchId, ImportRowStatus status);
}
