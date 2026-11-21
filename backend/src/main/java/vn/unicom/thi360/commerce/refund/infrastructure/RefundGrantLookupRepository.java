package vn.unicom.thi360.commerce.refund.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;

/**
 * Truy vấn các {@link EntitlementGrant} của một đơn hàng để thu hồi lượt chưa dùng khi hoàn hết khoản được tính
 * (TM 9.4 bước 6). Dùng cùng module {@code commerce} để khóa theo đúng thứ tự {@code entitlementId ASC, id ASC}.
 */
public interface RefundGrantLookupRepository extends JpaRepository<EntitlementGrant, UUID> {

    record GrantRef(UUID id, Instant revokedAt) {
    }

    @Query("""
            SELECT new vn.unicom.thi360.commerce.refund.infrastructure.RefundGrantLookupRepository$GrantRef(
                g.id, g.revokedAt)
            FROM EntitlementGrant g
            WHERE g.orderId = :orderId
            ORDER BY g.entitlementId ASC, g.id ASC
            """)
    List<GrantRef> findGrantRefsByOrderId(@Param("orderId") UUID orderId);
}
