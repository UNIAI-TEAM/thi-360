package vn.unicom.thi360.catalog.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.catalog.domain.CatalogItem;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    boolean existsByGroupId(UUID groupId);

    boolean existsByGroupIdAndCodeNormalized(UUID groupId, String codeNormalized);

    boolean existsByGroupIdAndCodeNormalizedAndIdNot(UUID groupId, String codeNormalized, UUID id);

    boolean existsByParentId(UUID parentId);

    long countByGroupId(UUID groupId);

    Optional<CatalogItem> findByGroupIdAndCodeNormalized(UUID groupId, String codeNormalized);

    List<CatalogItem> findByGroupIdAndIdIn(UUID groupId, Collection<UUID> ids);

    @Query("SELECT COALESCE(MAX(i.value), 0) FROM CatalogItem i WHERE i.groupId = :groupId")
    int findMaxValueByGroupId(@Param("groupId") UUID groupId);

    @Query(
            """
            SELECT COALESCE(MAX(i.displayOrder), -1)
            FROM CatalogItem i
            WHERE i.groupId = :groupId
              AND ((:parentId IS NULL AND i.parentId IS NULL) OR i.parentId = :parentId)
            """)
    int findMaxDisplayOrder(@Param("groupId") UUID groupId, @Param("parentId") UUID parentId);
}
