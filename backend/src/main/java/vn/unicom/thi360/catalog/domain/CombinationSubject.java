package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "combination_subject", schema = "catalog")
public class CombinationSubject {

    @EmbeddedId
    private CombinationSubjectId id;

    @Column(nullable = false)
    private int position;

    protected CombinationSubject() {
    }

    public CombinationSubject(UUID combinationVersionId, UUID subjectItemId, int position) {
        this.id = new CombinationSubjectId(combinationVersionId, subjectItemId);
        this.position = position;
    }

    public CombinationSubjectId getId() {
        return id;
    }

    public UUID getCombinationVersionId() {
        return id == null ? null : id.getCombinationVersionId();
    }

    public UUID getSubjectItemId() {
        return id == null ? null : id.getSubjectItemId();
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CombinationSubject that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
