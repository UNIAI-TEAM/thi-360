package vn.unicom.thi360.commerce.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Redemption record linking one promotion and one order (TM 7.1, 7.2, 8.6). */
@Entity
@Table(name = "promotion_redemption", schema = "commerce")
public class PromotionRedemption {

    @Id
    private UUID id;

    @Column(name = "promotion_id", nullable = false, updatable = false)
    private UUID promotionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_code")
    private String orderCode;

    @Column(name = "discount_vnd", nullable = false, updatable = false)
    private long discountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private RedemptionState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PromotionRedemption() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public long getDiscountVnd() {
        return discountVnd;
    }

    public RedemptionState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
