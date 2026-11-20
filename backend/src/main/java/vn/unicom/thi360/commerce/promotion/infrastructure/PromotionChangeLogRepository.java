package vn.unicom.thi360.commerce.promotion.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.promotion.domain.PromotionChangeLog;

public interface PromotionChangeLogRepository extends JpaRepository<PromotionChangeLog, UUID> {

    List<PromotionChangeLog> findByPromotionIdOrderByOccurredAtDescIdDesc(UUID promotionId);

    Page<PromotionChangeLog> findByPromotionIdOrderByOccurredAtDescIdDesc(UUID promotionId, Pageable pageable);
}
