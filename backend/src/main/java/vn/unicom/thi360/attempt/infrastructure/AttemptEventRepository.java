package vn.unicom.thi360.attempt.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;

@Repository
public interface AttemptEventRepository extends JpaRepository<AttemptEvent, UUID> {

    List<AttemptEvent> findByAttemptIdOrderByAtAscIdAsc(UUID attemptId);

    boolean existsByAttemptIdAndType(UUID attemptId, AttemptEventType type);

    @Query("""
            SELECT DISTINCT e.attemptId
            FROM AttemptEvent e
            WHERE e.attemptId IN :attemptIds
              AND e.type = :type
            """)
    List<UUID> findAttemptIdsWithType(
            @Param("attemptIds") Collection<UUID> attemptIds,
            @Param("type") AttemptEventType type);
}
