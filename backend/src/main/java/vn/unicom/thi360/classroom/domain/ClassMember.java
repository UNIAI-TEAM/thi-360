package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "class_member", schema = "classroom")
public class ClassMember {

    @EmbeddedId
    private ClassMemberId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "added_by")
    private UUID addedBy;

    protected ClassMember() {
    }

    public ClassMember(UUID classId, UUID userId, Instant joinedAt, UUID addedBy) {
        this.id = new ClassMemberId(classId, userId);
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.addedBy = addedBy;
    }

    public ClassMemberId getId() {
        return id;
    }

    public UUID getClassId() {
        return id == null ? null : id.getClassId();
    }

    public UUID getUserId() {
        return id == null ? null : id.getUserId();
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public UUID getAddedBy() {
        return addedBy;
    }
}
