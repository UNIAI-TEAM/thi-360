package vn.unicom.thi360.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Operational event log entry on an {@link Attempt} (LT §4, PBI-11 §7). */
@Entity
@Table(name = "attempt_event", schema = "attempt")
public class AttemptEvent {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private AttemptEventType type;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "part_id", updatable = false)
    private UUID partId;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> data;

    protected AttemptEvent() {
    }

    public AttemptEvent(
            UUID id,
            UUID attemptId,
            AttemptEventType type,
            UUID sessionId,
            UUID partId,
            Instant at,
            Map<String, Object> data) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.sessionId = sessionId;
        this.partId = partId;
        this.at = Objects.requireNonNull(at, "at must not be null");
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public AttemptEventType getType() {
        return type;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPartId() {
        return partId;
    }

    public Instant getAt() {
        return at;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
