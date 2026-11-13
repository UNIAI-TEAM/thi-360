package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Entity
@Table(name = "catalog_item", schema = "catalog")
public class CatalogItem {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private String code;

    @Column(name = "code_normalized", nullable = false)
    private String codeNormalized;

    @Column(nullable = false)
    private int value;

    @Column(name = "name_vi", nullable = false)
    private String nameVi;

    @Column(name = "name_en")
    private String nameEn;

    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, columnDefinition = "smallint")
    private CatalogStatus status;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private short depth;

    @Column(nullable = false)
    private String path;

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

    protected CatalogItem() {
    }

    public CatalogItem(
            UUID id,
            UUID groupId,
            String code,
            int value,
            String nameVi,
            String nameEn,
            String description,
            int displayOrder,
            CatalogStatus status,
            boolean system,
            UUID parentId,
            short depth,
            String path,
            Instant now,
            UUID actorId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null").trim();
        this.codeNormalized = CatalogNormalizer.normalizeCode(this.code);
        this.value = value;
        this.nameVi = Objects.requireNonNull(nameVi, "nameVi must not be null").trim();
        this.nameEn = CatalogNormalizer.trimToNull(nameEn);
        this.description = CatalogNormalizer.trimToNull(description);
        this.displayOrder = displayOrder;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.system = system;
        this.parentId = parentId;
        this.depth = depth;
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void updateDetails(
            String code,
            String nameVi,
            String nameEn,
            String description,
            Integer displayOrder,
            CatalogStatus status,
            Instant now,
            UUID actorId) {
        if (code != null) {
            this.code = code.trim();
            this.codeNormalized = CatalogNormalizer.normalizeCode(this.code);
        }
        if (nameVi != null) {
            this.nameVi = nameVi.trim();
        }
        if (nameEn != null) {
            this.nameEn = CatalogNormalizer.trimToNull(nameEn);
        }
        if (description != null) {
            this.description = CatalogNormalizer.trimToNull(description);
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void updateDisplayOrder(int displayOrder, Instant now, UUID actorId) {
        this.displayOrder = displayOrder;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void markUpdated(Instant now, UUID actorId) {
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getCode() {
        return code;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public int getValue() {
        return value;
    }

    public String getNameVi() {
        return nameVi;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public boolean isSystem() {
        return system;
    }

    public UUID getParentId() {
        return parentId;
    }

    public short getDepth() {
        return depth;
    }

    public String getPath() {
        return path;
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
