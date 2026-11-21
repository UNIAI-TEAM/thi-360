package vn.unicom.thi360.commerce.bundle.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.bundle.domain.BundleItem;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {

    List<BundleItem> findByBundleIdOrderByPositionAsc(UUID bundleId);

    List<BundleItem> findByBundleIdInOrderByBundleIdAscPositionAsc(Collection<UUID> bundleIds);

    void deleteByBundleId(UUID bundleId);
}
