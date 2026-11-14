package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ClassMemberId implements Serializable {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected ClassMemberId() {
    }

    public ClassMemberId(UUID classId, UUID userId) {
        this.classId = Objects.requireNonNull(classId, "classId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassMemberId that)) {
            return false;
        }
        return Objects.equals(classId, that.classId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classId, userId);
    }
}
