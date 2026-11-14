package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "combination_version", schema = "catalog")
public class CombinationVersion {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "exam_item_id", nullable = false)
    private UUID examItemId;

    @Column(name = "source_note")
    private String sourceNote;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CombinationVersion() {
    }

    public CombinationVersion(
            UUID id,
            UUID itemId,
            int versionNo,
            UUID examItemId,
            String sourceNote,
            boolean current,
            UUID createdBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.versionNo = versionNo;
        this.examItemId = Objects.requireNonNull(examItemId, "examItemId must not be null");
        this.sourceNote = CatalogNormalizer.trimToNull(sourceNote);
        this.current = current;
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public void replaceInPlace(UUID examItemId, String sourceNote) {
        this.examItemId = Objects.requireNonNull(examItemId, "examItemId must not be null");
        this.sourceNote = CatalogNormalizer.trimToNull(sourceNote);
    }

    public void markSuperseded() {
        this.current = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public UUID getExamItemId() {
        return examItemId;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public boolean isCurrent() {
        return current;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
