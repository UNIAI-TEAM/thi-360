package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogGroupRefId implements Serializable {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "ref_group_id", nullable = false)
    private UUID refGroupId;

    protected CatalogGroupRefId() {
    }

    public CatalogGroupRefId(UUID groupId, UUID refGroupId) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.refGroupId = Objects.requireNonNull(refGroupId, "refGroupId must not be null");
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getRefGroupId() {
        return refGroupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CatalogGroupRefId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId) && Objects.equals(refGroupId, that.refGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, refGroupId);
    }
}
