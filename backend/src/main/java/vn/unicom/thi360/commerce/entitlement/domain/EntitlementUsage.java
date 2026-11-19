package vn.unicom.thi360.commerce.entitlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One attempt of the attempt module drawn from one grant; {@code attemptId} is unique (TM 8.5). */
@Entity
@Table(name = "entitlement_usage", schema = "commerce")
public class EntitlementUsage {

    @Id
    private UUID id;

    @Column(name = "grant_id", nullable = false, updatable = false)
    private UUID grantId;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private UsageState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected EntitlementUsage() {
    }

    public EntitlementUsage(UUID id, UUID grantId, UUID attemptId, Instant now) {
        this.id = id;
        this.grantId = grantId;
        this.attemptId = attemptId;
        this.state = UsageState.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public UsageState getState() {
        return state;
    }

    public void markUsed(Instant now) {
        state = UsageState.USED;
        updatedAt = now;
    }

    public void markReleased(Instant now) {
        state = UsageState.RELEASED;
        updatedAt = now;
    }
}
