package vn.unicom.thi360.commerce.promotion.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.promotion.domain.PromotionRedemption;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, UUID> {

    Optional<PromotionRedemption> findByPromotionIdAndOrderId(UUID promotionId, UUID orderId);

    boolean existsByPromotionIdAndOrderIdAndState(UUID promotionId, UUID orderId, RedemptionState state);

    @Query("""
            SELECT COUNT(r) FROM PromotionRedemption r
            WHERE r.promotionId = :promotionId
              AND r.userId = :userId
              AND (r.state = vn.unicom.thi360.commerce.promotion.domain.RedemptionState.RESERVED
                   OR r.state = vn.unicom.thi360.commerce.promotion.domain.RedemptionState.CONFIRMED)
              AND (:excludedOrderId IS NULL
                   OR NOT (r.state = vn.unicom.thi360.commerce.promotion.domain.RedemptionState.RESERVED
                           AND r.orderId = :excludedOrderId))
            """)
    long countActiveForUserExcludingPendingOrder(
            @Param("promotionId") UUID promotionId,
            @Param("userId") UUID userId,
            @Param("excludedOrderId") UUID excludedOrderId);

    @Query("""
            SELECT COALESCE(SUM(r.discountVnd), 0) FROM PromotionRedemption r
            WHERE r.promotionId = :promotionId
              AND r.state = vn.unicom.thi360.commerce.promotion.domain.RedemptionState.CONFIRMED
            """)
    long sumConfirmedDiscountByPromotionId(@Param("promotionId") UUID promotionId);

    Page<PromotionRedemption> findByPromotionIdOrderByCreatedAtDescIdDesc(UUID promotionId, Pageable pageable);
}
