package vn.unicom.thi360.commerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lịch sử chuyển trạng thái của đơn hàng (TM 8.1). */
@Entity
@Table(name = "order_history", schema = "commerce")
public class OrderHistory {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "from_state", updatable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false, updatable = false)
    private String toState;

    @Column(name = "actor", nullable = false, updatable = false)
    private String actor;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    protected OrderHistory() {
    }

    public OrderHistory(UUID id, UUID orderId, OrderState fromState, OrderState toState, String actor, String reason,
            Instant at) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.fromState = fromState == null ? null : fromState.name();
        this.toState = Objects.requireNonNull(toState, "toState must not be null").name();
        this.actor = Objects.requireNonNull(actor, "actor must not be null");
        this.reason = reason;
        this.at = Objects.requireNonNull(at, "at must not be null");
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
}
