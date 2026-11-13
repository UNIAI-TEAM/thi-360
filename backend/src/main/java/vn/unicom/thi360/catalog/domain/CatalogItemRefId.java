package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogItemRefId implements Serializable {

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "ref_item_id", nullable = false)
    private UUID refItemId;

    protected CatalogItemRefId() {
    }

    public CatalogItemRefId(UUID itemId, UUID refItemId) {
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.refItemId = Objects.requireNonNull(refItemId, "refItemId must not be null");
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getRefItemId() {
        return refItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CatalogItemRefId that)) {
            return false;
        }
        return Objects.equals(itemId, that.itemId) && Objects.equals(refItemId, that.refItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, refItemId);
    }
}
