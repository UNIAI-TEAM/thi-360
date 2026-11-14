package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CombinationSubjectId implements Serializable {

    private UUID combinationVersionId;
    private UUID subjectItemId;

    protected CombinationSubjectId() {
    }

    public CombinationSubjectId(UUID combinationVersionId, UUID subjectItemId) {
        this.combinationVersionId = Objects.requireNonNull(combinationVersionId, "combinationVersionId must not be null");
        this.subjectItemId = Objects.requireNonNull(subjectItemId, "subjectItemId must not be null");
    }

    public UUID getCombinationVersionId() {
        return combinationVersionId;
    }

    public UUID getSubjectItemId() {
        return subjectItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CombinationSubjectId that)) {
            return false;
        }
        return Objects.equals(combinationVersionId, that.combinationVersionId)
                && Objects.equals(subjectItemId, that.subjectItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(combinationVersionId, subjectItemId);
    }
}
