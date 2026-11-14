package vn.unicom.thi360.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event", schema = "shared")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String topic, String payload, Instant createdAt) {
        this(id, topic, payload, null, createdAt);
    }

    public OutboxEvent(UUID id, String topic, String payload, String traceId, Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.payload = payload;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
        this.attempts = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void markProcessed(Instant processedAt) {
        this.processedAt = processedAt;
        this.failedAt = null;
        this.lastError = null;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void recordFailure(String error, Instant failureTime, int maxAttempts, Duration retryBackoff) {
        this.attempts++;
        this.lastError = error;
        if (this.attempts >= maxAttempts) {
            this.failedAt = failureTime;
        } else {
            this.nextAttemptAt = failureTime.plus(retryBackoff);
        }
    }

    public void markFailedPermanently(String error, Instant failedAt) {
        this.attempts++;
        this.lastError = error;
        this.failedAt = failedAt;
    }
}
