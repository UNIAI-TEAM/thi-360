package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_role", schema = "identity")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected UserRole() {
    }

    public UserRole(UUID userId, UUID roleId, UUID assignedBy, Instant assignedAt) {
        this.id = new UserRoleId(userId, roleId);
        this.assignedBy = assignedBy;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
    }

    public UserRoleId getId() {
        return id;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
