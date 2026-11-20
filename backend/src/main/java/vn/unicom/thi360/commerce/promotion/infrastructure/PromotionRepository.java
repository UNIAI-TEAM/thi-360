package vn.unicom.thi360.commerce.promotion.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;

public interface PromotionRepository extends JpaRepository<Promotion, UUID>, JpaSpecificationExecutor<Promotion> {

    Optional<Promotion> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Promotion> lockActiveById(@Param("id") UUID id);

    Optional<Promotion> findByCodeNormalizedAndKind(String codeNormalized, PromotionKind kind);

    boolean existsByCodeNormalized(String codeNormalized);

    boolean existsByCodeNormalizedAndIdNot(String codeNormalized, UUID id);

    /**
     * All currently eligible automatic promotions at {@code now}, excluding exhausted ones (TM 7.2).
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.kind = vn.unicom.thi360.commerce.promotion.domain.PromotionKind.AUTO
              AND p.deletedAt IS NULL
              AND p.status = vn.unicom.thi360.commerce.promotion.domain.PromotionStatus.ACTIVE
              AND p.startsAt <= :now
              AND p.endsAt > :now
              AND (p.totalLimit IS NULL OR p.usedCount < p.totalLimit)
            """)
    List<Promotion> findActiveAutoPromotions(@Param("now") Instant now);

    /**
     * Active automatic promotions at {@code now}, also keeping promotions whose last slot is held by
     * {@code (userId, excludedOrderId)} in {@code RESERVED} state while excluding over-limit promotions (TM 7.2 step 7,
     * AC-15, AC-18).
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.kind = vn.unicom.thi360.commerce.promotion.domain.PromotionKind.AUTO
              AND p.deletedAt IS NULL
              AND p.status = vn.unicom.thi360.commerce.promotion.domain.PromotionStatus.ACTIVE
              AND p.startsAt <= :now
              AND p.endsAt > :now
              AND (p.totalLimit IS NULL
                   OR p.usedCount < p.totalLimit
                   OR ((p.usedCount - 1) < p.totalLimit
                       AND EXISTS (SELECT 1 FROM PromotionRedemption r
                                   WHERE r.promotionId = p.id
                                     AND r.userId = :userId
                                     AND r.orderId = :excludedOrderId
                                     AND r.state = vn.unicom.thi360.commerce.promotion.domain.RedemptionState.RESERVED)))
            """)
    List<Promotion> findActiveAutoPromotionsExcludingPendingOrder(
            @Param("now") Instant now,
            @Param("userId") UUID userId,
            @Param("excludedOrderId") UUID excludedOrderId);
}
