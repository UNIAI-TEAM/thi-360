package vn.unicom.thi360.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowStatus;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    List<ImportRow> findByBatchIdOrderByRowNoAsc(UUID batchId);

    List<ImportRow> findByBatchIdAndStatusOrderByRowNoAsc(UUID batchId, ImportRowStatus status);

    List<ImportRow> findByBatchIdAndStatusInOrderByRowNoAsc(UUID batchId, Collection<ImportRowStatus> statuses);

    Page<ImportRow> findByBatchId(UUID batchId, Pageable pageable);

    Page<ImportRow> findByBatchIdAndStatus(UUID batchId, ImportRowStatus status, Pageable pageable);

    boolean existsByBatchIdAndClassCodeNormalizedIsNotNull(UUID batchId);

    int countByBatchIdAndStatus(UUID batchId, ImportRowStatus status);
}
