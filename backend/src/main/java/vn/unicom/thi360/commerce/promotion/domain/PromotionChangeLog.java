package vn.unicom.thi360.commerce.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable change-history entry written in the same transaction as every promotion write (BS §10). */
@Entity
@Table(name = "promotion_change_log", schema = "commerce")
public class PromotionChangeLog {

    @Id
    private UUID id;

    @Column(name = "promotion_id", nullable = false, updatable = false)
    private UUID promotionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private PromotionChangeAction action;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_username", updatable = false)
    private String actorUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<PromotionFieldChange> changes = List.of();

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PromotionChangeLog() {
    }

    public PromotionChangeLog(UUID id, UUID promotionId, PromotionChangeAction action, UUID actorId,
            String actorUsername, List<PromotionFieldChange> changes, Instant occurredAt) {
        this.id = id;
        this.promotionId = promotionId;
        this.action = action;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.changes = changes == null ? List.of() : List.copyOf(changes);
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public PromotionChangeAction getAction() {
        return action;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public List<PromotionFieldChange> getChanges() {
        return changes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
