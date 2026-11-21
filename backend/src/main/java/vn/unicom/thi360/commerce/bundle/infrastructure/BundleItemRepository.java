package vn.unicom.thi360.commerce.bundle.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.bundle.domain.BundleItem;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {

    List<BundleItem> findByBundleIdOrderByPositionAsc(UUID bundleId);

    List<BundleItem> findByBundleIdInOrderByBundleIdAscPositionAsc(Collection<UUID> bundleIds);

    void deleteByBundleId(UUID bundleId);

    @Query("""
            SELECT DISTINCT b.id
            FROM Bundle b
            JOIN BundleItem bi ON bi.bundleId = b.id
            WHERE bi.examId = :examId
              AND b.status = vn.unicom.thi360.commerce.bundle.domain.BundleStatus.PUBLISHED
            """)
    List<UUID> findPublishedBundleIdsContainingExam(@Param("examId") UUID examId);
}
