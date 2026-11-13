package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "class_room", schema = "classroom")
public class ClassRoom {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "code_normalized", nullable = false)
    private String codeNormalized;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "status", nullable = false)
    private ClassStatus status = ClassStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "delete_reason")
    private String deleteReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected ClassRoom() {
    }

    public ClassRoom(
            UUID id,
            String code,
            String name,
            String description,
            UUID ownerUserId,
            ClassStatus status,
            Instant now,
            UUID actorId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null").trim();
        this.codeNormalized = ClassNormalizer.normalizeCode(this.code);
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.description = ClassNormalizer.trimToNull(description);
        this.ownerUserId = ownerUserId;
        this.status = status == null ? ClassStatus.ACTIVE : status;
        Instant ts = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = ts;
        this.createdBy = actorId;
        this.updatedAt = ts;
        this.updatedBy = actorId;
    }

    public void updateDetails(
            String newCode,
            String newName,
            String newDescription,
            boolean updateDescription,
            UUID newOwnerUserId,
            boolean updateOwner,
            ClassStatus newStatus,
            Instant now,
            UUID actorId) {
        if (newCode != null) {
            this.code = newCode.trim();
            this.codeNormalized = ClassNormalizer.normalizeCode(this.code);
        }
        if (newName != null) {
            this.name = newName.trim();
        }
        if (updateDescription) {
            this.description = ClassNormalizer.trimToNull(newDescription);
        }
        if (updateOwner) {
            this.ownerUserId = newOwnerUserId;
        }
        if (newStatus != null) {
            this.status = newStatus;
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void updateStatus(ClassStatus newStatus, Instant now, UUID actorId) {
        this.status = Objects.requireNonNull(newStatus, "newStatus must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void softDelete(String reason, Instant now, UUID actorId) {
        Instant ts = Objects.requireNonNull(now, "now must not be null");
        this.deletedAt = ts;
        this.deletedBy = actorId;
        this.deleteReason = ClassNormalizer.trimToNull(reason);
        this.updatedAt = ts;
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

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public ClassStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == ClassStatus.ACTIVE && deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public String getDeleteReason() {
        return deleteReason;
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
