package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "class_member_history", schema = "classroom")
public class ClassMemberHistory {

    @Id
    private UUID id;

    @Column(name = "seq", nullable = false, insertable = false, updatable = false)
    private Long seq;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action", nullable = false)
    private ClassMemberHistoryAction action;

    @Column(name = "related_class_id")
    private UUID relatedClassId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClassMemberHistory() {
    }

    public ClassMemberHistory(
            UUID id,
            UUID classId,
            UUID userId,
            ClassMemberHistoryAction action,
            UUID relatedClassId,
            UUID actorId,
            String reason,
            Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classId = Objects.requireNonNull(classId, "classId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.relatedClassId = relatedClassId;
        this.actorId = actorId;
        this.reason = ClassNormalizer.trimToNull(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ClassMemberHistoryAction getAction() {
        return action;
    }

    public UUID getRelatedClassId() {
        return relatedClassId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
