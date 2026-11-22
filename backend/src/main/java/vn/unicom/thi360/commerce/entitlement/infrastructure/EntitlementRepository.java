package vn.unicom.thi360.commerce.entitlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    /**
     * Creates the empty purchase entitlement of (user, exam) unless it exists. Concurrent callers wait on the
     * unique constraint, so exactly one row results (TM 8.5). Returns 1 when this call inserted it.
     */
    @Modifying
    @Query(value = """
            INSERT INTO commerce.entitlement
                (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used, attempts_reserved,
                 attempts_revoked, created_at, updated_at, version)
            VALUES (:id, :userId, :examId, 'PURCHASE', NULL, 0, 0, 0, 0, :now, :now, 0)
            ON CONFLICT ON CONSTRAINT uq_entitlement_scope DO NOTHING
            """, nativeQuery = true)
    int insertPurchaseIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("examId") UUID examId,
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Entitlement e
            WHERE e.userId = :userId AND e.examId = :examId
              AND e.source = vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource.PURCHASE
              AND e.sourceRef IS NULL
            """)
    Optional<Entitlement> lockPurchase(@Param("userId") UUID userId, @Param("examId") UUID examId);

    @Modifying
    @Query(value = """
            INSERT INTO commerce.entitlement
                (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used, attempts_reserved,
                 attempts_revoked, created_at, updated_at, version)
            VALUES (:id, :userId, :examId, 'CLASS', :classId, 0, 0, 0, 0, :now, :now, 0)
            ON CONFLICT ON CONSTRAINT uq_entitlement_scope DO NOTHING
            """, nativeQuery = true)
    int insertClassIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("examId") UUID examId,
            @Param("classId") UUID classId,
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Entitlement e
            WHERE e.userId = :userId AND e.examId = :examId
              AND e.source = vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource.CLASS
              AND e.sourceRef = :classId
            """)
    Optional<Entitlement> lockClass(
            @Param("userId") UUID userId,
            @Param("examId") UUID examId,
            @Param("classId") UUID classId);

    @Query("""
            SELECT e.id FROM Entitlement e
            WHERE e.userId = :userId
              AND e.source = vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource.CLASS
              AND e.sourceRef = :classId
            ORDER BY e.id ASC
            """)
    List<UUID> findClassEntitlementIdsByUserAndClass(
            @Param("userId") UUID userId,
            @Param("classId") UUID classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Entitlement e WHERE e.id = :id")
    Optional<Entitlement> lockById(@Param("id") UUID id);

    /**
     * The statement behind {@link #existsByUserId}, shared with the test that explains its plan: the lookup must
     * stay an index condition on {@code user_id} (the leading column of {@code uq_entitlement_scope}).
     */
    String EXISTS_BY_USER_ID_SQL = "SELECT EXISTS (SELECT 1 FROM commerce.entitlement e WHERE e.user_id = :userId)";

    @Query(value = EXISTS_BY_USER_ID_SQL, nativeQuery = true)
    boolean existsByUserId(@Param("userId") UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    List<Entitlement> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);
}
