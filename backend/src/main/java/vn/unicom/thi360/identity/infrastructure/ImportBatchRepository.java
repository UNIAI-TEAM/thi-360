package vn.unicom.thi360.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.domain.ImportBatch;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ImportBatch b WHERE b.id = :id")
    Optional<ImportBatch> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ImportBatch b
            WHERE b.id = :id
              AND b.status = vn.unicom.thi360.identity.domain.ImportBatchStatus.PROCESSING
              AND b.leaseToken = :leaseToken
            """)
    Optional<ImportBatch> findOwnedForUpdate(
            @Param("id") UUID id,
            @Param("leaseToken") UUID leaseToken);

    boolean existsByIdAndStatusAndLeaseToken(
            UUID id,
            ImportBatchStatus status,
            UUID leaseToken);
}
