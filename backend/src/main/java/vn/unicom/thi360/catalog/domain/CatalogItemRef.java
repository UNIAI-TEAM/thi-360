package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "catalog_item_ref", schema = "catalog")
public class CatalogItemRef {

    @EmbeddedId
    private CatalogItemRefId id;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CatalogItemRef() {
    }

    public CatalogItemRef(UUID itemId, UUID refItemId, UUID createdBy, Instant createdAt) {
        this.id = new CatalogItemRefId(itemId, refItemId);
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public CatalogItemRefId getId() {
        return id;
    }

    public UUID getItemId() {
        return id.getItemId();
    }

    public UUID getRefItemId() {
        return id.getRefItemId();
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
