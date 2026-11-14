package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Entity
@Table(name = "permission", schema = "identity")
public class PermissionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "bit_index", nullable = false, unique = true)
    private int bitIndex;

    @Column(nullable = false, columnDefinition = "smallint")
    private Access scope;

    @Column(nullable = false)
    private String name;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PermissionEntity() {
    }

    public PermissionEntity(
            UUID id,
            String code,
            int bitIndex,
            Access scope,
            String name,
            String groupCode,
            String description,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.bitIndex = bitIndex;
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.groupCode = Objects.requireNonNull(groupCode, "groupCode must not be null");
        this.description = description;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public boolean syncMetadata(Access scope, String name, String groupCode, String description, Instant now) {
        boolean changed = false;
        if (this.scope != scope) {
            this.scope = Objects.requireNonNull(scope);
            changed = true;
        }
        if (!Objects.equals(this.name, name)) {
            this.name = Objects.requireNonNull(name);
            changed = true;
        }
        if (!Objects.equals(this.groupCode, groupCode)) {
            this.groupCode = Objects.requireNonNull(groupCode);
            changed = true;
        }
        if (!Objects.equals(this.description, description)) {
            this.description = description;
            changed = true;
        }
        if (changed) {
            this.updatedAt = Objects.requireNonNull(now);
        }
        return changed;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public Access getScope() {
        return scope;
    }

    public String getName() {
        return name;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
