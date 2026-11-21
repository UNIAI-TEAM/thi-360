package vn.unicom.thi360.attempt.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptItem;

@Repository
public interface AttemptItemRepository extends JpaRepository<AttemptItem, UUID> {

    List<AttemptItem> findByAttemptIdOrderByPartIdAscOrdAsc(UUID attemptId);

    List<AttemptItem> findByPartIdOrderByOrdAsc(UUID partId);

    List<AttemptItem> findByAttemptIdAndPartIdInOrderByPartIdAscOrdAsc(UUID attemptId, Collection<UUID> partIds);

    boolean existsByPartId(UUID partId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AttemptItem i WHERE i.attemptId = :attemptId AND i.id IN :ids ORDER BY i.id ASC")
    List<AttemptItem> findByAttemptIdAndIdInOrderByIdAscForUpdate(
            @Param("attemptId") UUID attemptId, @Param("ids") Collection<UUID> ids);
}
