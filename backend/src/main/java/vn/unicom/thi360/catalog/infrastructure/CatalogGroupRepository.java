package vn.unicom.thi360.catalog.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.catalog.domain.CatalogGroup;

public interface CatalogGroupRepository extends JpaRepository<CatalogGroup, UUID> {

    Optional<CatalogGroup> findByCodeNormalized(String codeNormalized);

    boolean existsByCodeNormalized(String codeNormalized);

    boolean existsByCodeNormalizedAndIdNot(String codeNormalized, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM CatalogGroup g WHERE g.id = :id")
    Optional<CatalogGroup> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CatalogGroup g SET g.lastValue = :lastValue WHERE g.id = :id AND g.lastValue < :lastValue")
    void advanceLastValue(@Param("id") UUID id, @Param("lastValue") int lastValue);
}
