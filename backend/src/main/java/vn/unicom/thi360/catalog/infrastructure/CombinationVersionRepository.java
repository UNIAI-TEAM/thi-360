package vn.unicom.thi360.catalog.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.catalog.domain.CombinationVersion;

public interface CombinationVersionRepository extends JpaRepository<CombinationVersion, UUID> {

    Optional<CombinationVersion> findByItemIdAndCurrentTrue(UUID itemId);

    List<CombinationVersion> findByItemIdOrderByVersionNoDesc(UUID itemId);
}
