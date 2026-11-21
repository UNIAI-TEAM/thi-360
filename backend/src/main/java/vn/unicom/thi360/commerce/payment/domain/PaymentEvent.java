package vn.unicom.thi360.commerce.payment.domain;

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

/** Lưu nguyên văn mọi lần VNPAY gọi hoặc phản hồi để đối soát (TM 8.1). */
@Entity
@Table(name = "payment_event", schema = "commerce")
public class PaymentEvent {

    @Id
    private UUID id;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private PaymentEventSource source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> raw;

    @Column(name = "signature_valid", nullable = false, updatable = false)
    private boolean signatureValid;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "outcome", nullable = false, updatable = false)
    private String outcome;

    protected PaymentEvent() {
    }

    public PaymentEvent(
            UUID id,
            UUID paymentId,
            PaymentEventSource source,
            Map<String, Object> raw,
            boolean signatureValid,
            Instant receivedAt,
            String outcome) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.paymentId = paymentId;
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.raw = raw == null ? Map.of() : Map.copyOf(raw);
        this.signatureValid = signatureValid;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public PaymentEventSource getSource() { return source; }
    public Map<String, Object> getRaw() { return raw; }
    public boolean isSignatureValid() { return signatureValid; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getOutcome() { return outcome; }
}
