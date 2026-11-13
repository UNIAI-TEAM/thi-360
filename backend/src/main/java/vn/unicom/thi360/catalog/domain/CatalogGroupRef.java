package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "catalog_group_ref", schema = "catalog")
public class CatalogGroupRef {

    @EmbeddedId
    private CatalogGroupRefId id;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRef;

    protected CatalogGroupRef() {
    }

    public CatalogGroupRef(UUID groupId, UUID refGroupId, int position, boolean defaultRef) {
        this.id = new CatalogGroupRefId(groupId, refGroupId);
        this.position = position;
        this.defaultRef = defaultRef;
    }

    public CatalogGroupRefId getId() {
        return id;
    }

    public UUID getGroupId() {
        return id.getGroupId();
    }

    public UUID getRefGroupId() {
        return id.getRefGroupId();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isDefaultRef() {
        return defaultRef;
    }
}
