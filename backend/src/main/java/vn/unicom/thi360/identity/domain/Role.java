package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Entity
@Table(name = "role", schema = "identity")
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "code_normalized", nullable = false, unique = true)
    private String codeNormalized;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "smallint")
    private Access access;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(nullable = false, columnDefinition = "smallint")
    private RoleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Role() {
    }

    public Role(
            UUID id,
            String code,
            String name,
            String description,
            Access access,
            boolean system,
            RoleStatus status,
            Instant now,
            UUID actorId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null").trim();
        this.codeNormalized = Normalizer.normalize(this.code);
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.system = system;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Access getAccess() {
        return access;
    }

    public boolean isSystem() {
        return system;
    }

    public RoleStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
