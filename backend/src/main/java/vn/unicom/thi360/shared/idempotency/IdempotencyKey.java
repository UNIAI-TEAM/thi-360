package vn.unicom.thi360.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_key", schema = "shared")
public class IdempotencyKey {

    @EmbeddedId
    private IdempotencyKeyId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", nullable = false, columnDefinition = "jsonb")
    private String response;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(IdempotencyKeyId id, String response, Instant createdAt) {
        this.id = id;
        this.response = response;
        this.createdAt = createdAt;
    }

    public IdempotencyKeyId getId() {
        return id;
    }

    public String getScope() {
        return id.getScope();
    }

    public String getKey() {
        return id.getKey();
    }

    public String getResponse() {
        return response;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
