package vn.unicom.thi360.commerce.bundle.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;

public interface BundleRepository extends JpaRepository<Bundle, UUID>, JpaSpecificationExecutor<Bundle> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bundle b WHERE b.id = :id")
    Optional<Bundle> lockById(@Param("id") UUID id);

    boolean existsByCodeNormalized(String codeNormalized);

    boolean existsByCodeNormalizedAndIdNot(String codeNormalized, UUID id);

    Optional<Bundle> findByCodeNormalized(String codeNormalized);

    List<Bundle> findByStatusInOrderByCodeNormalizedAsc(List<BundleStatus> statuses);

    List<Bundle> findByIdIn(List<UUID> ids);
}
