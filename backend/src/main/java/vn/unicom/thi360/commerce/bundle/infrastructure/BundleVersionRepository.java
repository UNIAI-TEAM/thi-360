package vn.unicom.thi360.commerce.bundle.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;

public interface BundleVersionRepository extends JpaRepository<BundleVersion, UUID> {

    Optional<BundleVersion> findByBundleIdAndVersionNo(UUID bundleId, int versionNo);
}
