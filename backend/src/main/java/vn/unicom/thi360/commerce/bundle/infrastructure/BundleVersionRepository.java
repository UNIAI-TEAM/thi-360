package vn.unicom.thi360.commerce.bundle.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;

public interface BundleVersionRepository extends JpaRepository<BundleVersion, UUID> {

    Optional<BundleVersion> findByBundleIdAndVersionNo(UUID bundleId, int versionNo);

    /** Returns IDs of PUBLISHED bundles whose live serving snapshot references {@code examId}. */
    @Query(value = """
            SELECT DISTINCT bv.bundle_id
            FROM commerce.bundle_version bv
            JOIN commerce.bundle b ON b.id = bv.bundle_id
            WHERE b.status = 'PUBLISHED'
              AND b.serving_version = bv.version_no
              AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements(bv.snapshot -> 'items') elem
                  WHERE elem ->> 'examId' = CAST(:examId AS text)
              )
            ORDER BY bv.bundle_id
            """, nativeQuery = true)
    List<UUID> findActivePublishedBundleIdsContainingExam(@Param("examId") UUID examId);
}
