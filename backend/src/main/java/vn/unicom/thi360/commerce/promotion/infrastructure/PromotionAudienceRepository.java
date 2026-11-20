package vn.unicom.thi360.commerce.promotion.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;

public interface PromotionAudienceRepository extends JpaRepository<PromotionAudience, UUID> {

    List<PromotionAudience> findByPromotionId(UUID promotionId);

    List<PromotionAudience> findByPromotionIdIn(Collection<UUID> promotionIds);

    boolean existsByPromotionIdAndUserId(UUID promotionId, UUID userId);

    void deleteByPromotionId(UUID promotionId);
}
