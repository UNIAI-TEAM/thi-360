package vn.unicom.thi360.commerce.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One eligible class or user when a voucher has {@code audience = SELECTED} (TM 7.1). */
@Entity
@Table(name = "promotion_audience", schema = "commerce")
public class PromotionAudience {

    @Id
    private UUID id;

    @Column(name = "promotion_id", nullable = false, updatable = false)
    private UUID promotionId;

    @Column(name = "class_id", updatable = false)
    private UUID classId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    protected PromotionAudience() {
    }

    private PromotionAudience(UUID id, UUID promotionId, UUID classId, UUID userId) {
        this.id = id;
        this.promotionId = promotionId;
        this.classId = classId;
        this.userId = userId;
    }

    public static PromotionAudience forClass(UUID id, UUID promotionId, UUID classId) {
        return new PromotionAudience(id, promotionId, classId, null);
    }

    public static PromotionAudience forUser(UUID id, UUID promotionId, UUID userId) {
        return new PromotionAudience(id, promotionId, null, userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getUserId() {
        return userId;
    }
}
