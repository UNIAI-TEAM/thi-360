package vn.unicom.thi360.identity.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    boolean existsByIdRoleId(UUID roleId);

    List<RolePermission> findAllByIdRoleId(UUID roleId);

    void deleteAllByIdRoleId(UUID roleId);
}
