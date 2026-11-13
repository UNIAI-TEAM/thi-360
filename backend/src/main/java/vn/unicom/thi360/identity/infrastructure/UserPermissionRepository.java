package vn.unicom.thi360.identity.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.identity.domain.UserPermission;
import vn.unicom.thi360.identity.domain.UserPermissionId;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    List<UserPermission> findAllByIdUserId(UUID userId);

    void deleteAllByIdUserId(UUID userId);
}
