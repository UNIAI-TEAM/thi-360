package vn.unicom.thi360.attempt.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Repository
public interface AttemptRepository
        extends JpaRepository<Attempt, UUID>, JpaSpecificationExecutor<Attempt> {

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
                    SELECT id FROM attempt.attempt
                    WHERE state = 'STARTING'
                      AND created_at < :cutoff
                    ORDER BY created_at ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<UUID> findStaleStartingIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query(
            value = """
                    SELECT * FROM attempt.attempt
                    WHERE id = :id
                      AND state = 'STARTING'
                      AND created_at < :cutoff
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    Optional<Attempt> lockStaleStartingById(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    Optional<Attempt> findFirstByUserIdAndEntitlementIdAndStateInOrderByCreatedAtDesc(
            UUID userId, UUID entitlementId, Collection<AttemptState> states);

    @Query(
            value = """
                    SELECT * FROM attempt.attempt
                    WHERE user_id = :userId
                      AND entitlement_id = :entitlementId
                      AND state IN ('GRADING', 'PENDING_REVIEW', 'SCORED', 'GRADING_ERROR')
                    ORDER BY finished_at DESC NULLS LAST, created_at DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<Attempt> findLatestResultAttempt(
            @Param("userId") UUID userId, @Param("entitlementId") UUID entitlementId);

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1 FROM attempt.attempt a
                        WHERE a.id = :attemptId
                          AND a.state = 'IN_PROGRESS'
                          AND (
                              (a.timing = 'SHARED' AND a.deadline_at IS NOT NULL AND a.deadline_at <= :now)
                              OR EXISTS (
                                  SELECT 1 FROM attempt.attempt_part p
                                  WHERE p.attempt_id = a.id
                                    AND (
                                        (a.timing = 'PER_PART' AND p.state = 'OPEN' AND p.deadline_at IS NOT NULL AND p.deadline_at <= :now)
                                        OR (p.state = 'AWAITING_CHOICE' AND p.choice_deadline_at IS NOT NULL AND p.choice_deadline_at <= :now)
                                    )
                              )
                          )
                    )
                    """,
            nativeQuery = true)
    boolean hasOverdueDeadline(@Param("attemptId") UUID attemptId, @Param("now") Instant now);

    @Query(
            value = """
                    SELECT due.id
                    FROM (
                        SELECT a.id AS id, a.deadline_at AS due_at
                        FROM attempt.attempt a
                        WHERE a.state = 'IN_PROGRESS'
                          AND a.timing = 'SHARED'
                          AND a.deadline_at IS NOT NULL
                          AND a.deadline_at <= :now
                        UNION ALL
                        SELECT a.id AS id, p.deadline_at AS due_at
                        FROM attempt.attempt a
                        JOIN attempt.attempt_part p ON p.attempt_id = a.id
                        WHERE a.state = 'IN_PROGRESS'
                          AND a.timing = 'PER_PART'
                          AND p.state = 'OPEN'
                          AND p.deadline_at IS NOT NULL
                          AND p.deadline_at <= :now
                        UNION ALL
                        SELECT a.id AS id, p.choice_deadline_at AS due_at
                        FROM attempt.attempt a
                        JOIN attempt.attempt_part p ON p.attempt_id = a.id
                        WHERE a.state = 'IN_PROGRESS'
                          AND p.state = 'AWAITING_CHOICE'
                          AND p.choice_deadline_at IS NOT NULL
                          AND p.choice_deadline_at <= :now
                    ) due
                    GROUP BY due.id
                    ORDER BY MIN(due.due_at) ASC, due.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<UUID> findOverdueAttemptIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(
            value = """
                    SELECT due.id
                    FROM (
                        SELECT a.id AS id, a.deadline_at AS due_at
                        FROM attempt.attempt a
                        WHERE a.state = 'IN_PROGRESS'
                          AND a.timing = 'SHARED'
                          AND a.deadline_at IS NOT NULL
                          AND a.deadline_at <= :now
                          AND a.id NOT IN (:excludedIds)
                        UNION ALL
                        SELECT a.id AS id, p.deadline_at AS due_at
                        FROM attempt.attempt a
                        JOIN attempt.attempt_part p ON p.attempt_id = a.id
                        WHERE a.state = 'IN_PROGRESS'
                          AND a.timing = 'PER_PART'
                          AND p.state = 'OPEN'
                          AND p.deadline_at IS NOT NULL
                          AND p.deadline_at <= :now
                          AND a.id NOT IN (:excludedIds)
                        UNION ALL
                        SELECT a.id AS id, p.choice_deadline_at AS due_at
                        FROM attempt.attempt a
                        JOIN attempt.attempt_part p ON p.attempt_id = a.id
                        WHERE a.state = 'IN_PROGRESS'
                          AND p.state = 'AWAITING_CHOICE'
                          AND p.choice_deadline_at IS NOT NULL
                          AND p.choice_deadline_at <= :now
                          AND a.id NOT IN (:excludedIds)
                    ) due
                    GROUP BY due.id
                    ORDER BY MIN(due.due_at) ASC, due.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<UUID> findOverdueAttemptIdsNotIn(
            @Param("now") Instant now,
            @Param("excludedIds") Collection<UUID> excludedIds,
            @Param("limit") int limit);

    default List<UUID> findOverdueAttemptIdsExcluding(
            Instant now, Collection<UUID> excludedIds, int limit) {
        if (excludedIds == null || excludedIds.isEmpty()) {
            return findOverdueAttemptIds(now, limit);
        }
        return findOverdueAttemptIdsNotIn(now, excludedIds, limit);
    }

    @Query(
            value = """
                    SELECT * FROM attempt.attempt
                    WHERE id = :id
                      AND state = 'IN_PROGRESS'
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    Optional<Attempt> lockInProgressByIdSkipLocked(@Param("id") UUID id);
}
