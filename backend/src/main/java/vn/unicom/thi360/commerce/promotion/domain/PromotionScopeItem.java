package vn.unicom.thi360.commerce.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One scope target of a promotion: exactly one of {@code examItemId}, {@code examId}, or {@code bundleId}. */
@Entity
@Table(name = "promotion_scope_item", schema = "commerce")
public class PromotionScopeItem {

    @Id
    private UUID id;

    @Column(name = "promotion_id", nullable = false, updatable = false)
    private UUID promotionId;

    @Column(name = "exam_item_id", updatable = false)
    private UUID examItemId;

    @Column(name = "exam_id", updatable = false)
    private UUID examId;

    @Column(name = "bundle_id", updatable = false)
    private UUID bundleId;

    protected PromotionScopeItem() {
    }

    private PromotionScopeItem(UUID id, UUID promotionId, UUID examItemId, UUID examId, UUID bundleId) {
        this.id = id;
        this.promotionId = promotionId;
        this.examItemId = examItemId;
        this.examId = examId;
        this.bundleId = bundleId;
    }

    public static PromotionScopeItem forExamKind(UUID id, UUID promotionId, UUID examItemId) {
        return new PromotionScopeItem(id, promotionId, examItemId, null, null);
    }

    public static PromotionScopeItem forExam(UUID id, UUID promotionId, UUID examId) {
        return new PromotionScopeItem(id, promotionId, null, examId, null);
    }

    public static PromotionScopeItem forBundle(UUID id, UUID promotionId, UUID bundleId) {
        return new PromotionScopeItem(id, promotionId, null, null, bundleId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public UUID getExamItemId() {
        return examItemId;
    }

    public UUID getExamId() {
        return examId;
    }

    public UUID getBundleId() {
        return bundleId;
    }
}
