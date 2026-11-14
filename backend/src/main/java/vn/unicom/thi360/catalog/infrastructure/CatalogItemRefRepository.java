package vn.unicom.thi360.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.catalog.domain.CatalogItemRef;
import vn.unicom.thi360.catalog.domain.CatalogItemRefId;

public interface CatalogItemRefRepository extends JpaRepository<CatalogItemRef, CatalogItemRefId> {

    List<CatalogItemRef> findByIdItemId(UUID itemId);

    void deleteAllByIdItemId(UUID itemId);

    @Query(
            """
            SELECT COUNT(DISTINCT cir.id.itemId)
            FROM CatalogItemRef cir
            JOIN CatalogItem src ON src.id = cir.id.itemId
            JOIN CatalogItem refItem ON refItem.id = cir.id.refItemId
            WHERE src.groupId = :groupId
              AND refItem.groupId = :refGroupId
            """)
    long countLinkedItems(@Param("groupId") UUID groupId, @Param("refGroupId") UUID refGroupId);
}
