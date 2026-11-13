package vn.unicom.thi360.shared.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(
            value = """
                    SELECT * FROM shared.outbox_event
                    WHERE processed_at IS NULL
                      AND failed_at IS NULL
                      AND attempts < :maxAttempts
                      AND next_attempt_at <= :now
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<OutboxEvent> findNextPendingBatchForUpdate(
            @Param("limit") int limit,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now);
}
