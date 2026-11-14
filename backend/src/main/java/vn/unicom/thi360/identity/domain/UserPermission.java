package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_permission", schema = "identity")
public class UserPermission {

    @EmbeddedId
    private UserPermissionId id;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(nullable = false)
    private String reason;

    protected UserPermission() {
    }

    public UserPermission(UUID userId, UUID permissionId, UUID grantedBy, Instant grantedAt, String reason) {
        this.id = new UserPermissionId(userId, permissionId);
        this.grantedBy = grantedBy;
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public UserPermissionId getId() {
        return id;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public String getReason() {
        return reason;
    }
}
