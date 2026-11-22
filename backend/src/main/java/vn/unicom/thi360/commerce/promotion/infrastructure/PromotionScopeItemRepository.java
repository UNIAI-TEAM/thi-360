package vn.unicom.thi360.commerce.promotion.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;

public interface PromotionScopeItemRepository extends JpaRepository<PromotionScopeItem, UUID> {

    List<PromotionScopeItem> findByPromotionId(UUID promotionId);

    List<PromotionScopeItem> findByPromotionIdIn(Collection<UUID> promotionIds);

    void deleteByPromotionId(UUID promotionId);
}
