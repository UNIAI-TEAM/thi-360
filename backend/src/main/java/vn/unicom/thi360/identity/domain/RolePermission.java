package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_permission", schema = "identity")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected RolePermission() {
    }

    public RolePermission(UUID roleId, UUID permissionId, UUID grantedBy, Instant grantedAt) {
        this.id = new RolePermissionId(roleId, permissionId);
        this.grantedBy = grantedBy;
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt must not be null");
    }

    public RolePermissionId getId() {
        return id;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
