package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Entity
@Table(name = "catalog_group", schema = "catalog")
public class CatalogGroup {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "code_normalized", nullable = false, unique = true)
    private String codeNormalized;

    @Column(name = "name_vi", nullable = false)
    private String nameVi;

    @Column(name = "name_en")
    private String nameEn;

    private String description;

    @Column(nullable = false, columnDefinition = "smallint")
    private CatalogStatus status;

    @Column(nullable = false, columnDefinition = "smallint")
    private CatalogGroupType type;

    @Column(nullable = false, columnDefinition = "smallint")
    private CatalogGroupKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "levels", columnDefinition = "jsonb")
    private List<String> levels;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "last_value", nullable = false, updatable = false)
    private int lastValue;

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

    protected CatalogGroup() {
    }

    public CatalogGroup(
            UUID id,
            String code,
            String nameVi,
            String nameEn,
            String description,
            CatalogStatus status,
            CatalogGroupType type,
            CatalogGroupKind kind,
            List<String> levels,
            Instant now,
            UUID actorId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null").trim();
        this.codeNormalized = CatalogNormalizer.normalizeCode(this.code);
        this.nameVi = Objects.requireNonNull(nameVi, "nameVi must not be null").trim();
        this.nameEn = CatalogNormalizer.trimToNull(nameEn);
        this.description = CatalogNormalizer.trimToNull(description);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.levels = levels == null ? null : List.copyOf(levels);
        this.system = type == CatalogGroupType.SYSTEM;
        this.lastValue = 0;
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
            CatalogStatus status,
            List<String> levels,
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
        if (status != null) {
            this.status = status;
        }
        if (levels != null) {
            this.levels = List.copyOf(levels);
        }
        this.updatedAt = advanceUpdatedAt(now);
        this.updatedBy = actorId;
    }

    public void markUpdated(Instant now, UUID actorId) {
        this.updatedAt = advanceUpdatedAt(now);
        this.updatedBy = actorId;
    }

    private Instant advanceUpdatedAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return this.updatedAt != null && !now.isAfter(this.updatedAt)
                ? this.updatedAt.plusNanos(1000)
                : now;
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

    public String getNameVi() {
        return nameVi;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public CatalogGroupType getType() {
        return type;
    }

    public CatalogGroupKind getKind() {
        return kind;
    }

    public List<String> getLevels() {
        return levels;
    }

    public boolean isSystem() {
        return system;
    }

    public int getLastValue() {
        return lastValue;
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
