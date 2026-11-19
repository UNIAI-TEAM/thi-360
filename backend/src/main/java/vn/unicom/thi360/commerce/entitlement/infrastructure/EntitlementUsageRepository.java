package vn.unicom.thi360.commerce.entitlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementUsage;

public interface EntitlementUsageRepository extends JpaRepository<EntitlementUsage, UUID> {

    Optional<EntitlementUsage> findByAttemptId(UUID attemptId);

    @Query("SELECT u.grantId FROM EntitlementUsage u WHERE u.attemptId = :attemptId")
    Optional<UUID> findGrantIdByAttemptId(@Param("attemptId") UUID attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM EntitlementUsage u WHERE u.attemptId = :attemptId")
    Optional<EntitlementUsage> lockByAttemptId(@Param("attemptId") UUID attemptId);
}
