package vn.unicom.thi360.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.catalog.domain.CatalogGroupRef;
import vn.unicom.thi360.catalog.domain.CatalogGroupRefId;

public interface CatalogGroupRefRepository extends JpaRepository<CatalogGroupRef, CatalogGroupRefId> {

    List<CatalogGroupRef> findByIdGroupIdOrderByPositionAsc(UUID groupId);

    boolean existsByIdRefGroupId(UUID refGroupId);

    void deleteAllByIdGroupId(UUID groupId);
}
