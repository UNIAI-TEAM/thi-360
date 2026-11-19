package vn.unicom.thi360.commerce.entitlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;

public interface EntitlementGrantRepository extends JpaRepository<EntitlementGrant, UUID> {

    Optional<EntitlementGrant> findByOrderIdAndExamId(UUID orderId, UUID examId);

    Optional<EntitlementGrant> findByCompensationAttemptId(UUID compensationAttemptId);

    @Query("SELECT g.entitlementId FROM EntitlementGrant g WHERE g.id = :id")
    Optional<UUID> findEntitlementIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM EntitlementGrant g WHERE g.id = :id")
    Optional<EntitlementGrant> lockById(@Param("id") UUID id);

    /** Oldest grant of the entitlement that still has an attempt to give (E21), without a lock (describe). */
    @Query("""
            SELECT g FROM EntitlementGrant g
            WHERE g.entitlementId = :entitlementId
              AND g.attempts - g.attemptsUsed - g.attemptsReserved - g.attemptsRevoked > 0
            ORDER BY g.grantedAt ASC, g.seq ASC
            """)
    List<EntitlementGrant> findWithAttemptsLeft(@Param("entitlementId") UUID entitlementId, Pageable page);

    /** As {@link #findWithAttemptsLeft} but locks the row; the entitlement row is already locked (B2 order). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT g FROM EntitlementGrant g
            WHERE g.entitlementId = :entitlementId
              AND g.attempts - g.attemptsUsed - g.attemptsReserved - g.attemptsRevoked > 0
            ORDER BY g.grantedAt ASC, g.seq ASC
            """)
    List<EntitlementGrant> lockWithAttemptsLeft(@Param("entitlementId") UUID entitlementId, Pageable page);
}
