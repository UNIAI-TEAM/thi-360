package vn.unicom.thi360.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.identity.domain.PlatformAdmin;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    List<PlatformAdmin> findAllByIdIn(Collection<UUID> ids);
}
