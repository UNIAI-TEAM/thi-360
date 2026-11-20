package vn.unicom.thi360.attempt.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    boolean existsByIdAndUserId(UUID id, UUID userId);

    Optional<Attempt> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attempt a WHERE a.id = :id")
    Optional<Attempt> findByIdForUpdate(@Param("id") UUID id);

    Optional<Attempt> findByUserIdAndExamIdAndStateIn(UUID userId, UUID examId, Collection<AttemptState> states);

    boolean existsByUserIdAndStateIn(UUID userId, Collection<AttemptState> states);

    List<Attempt> findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
            UUID userId, Collection<AttemptState> excludedStates);

    @Query(
            value = """
                    SELECT COALESCE(MAX(attempt_no), 0) + 1
                    FROM attempt.attempt
                    WHERE user_id = :userId
                      AND exam_id = :examId
                      AND attempt_no IS NOT NULL
                    """,
            nativeQuery = true)
    int findNextAttemptNo(@Param("userId") UUID userId, @Param("examId") UUID examId);

    @Query(
            value = """
                    SELECT * FROM attempt.attempt
                    WHERE state = 'STARTING'
                      AND created_at < :cutoff
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<Attempt> findStaleStartingForUpdate(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
