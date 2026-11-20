package vn.unicom.thi360.commerce.order.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    String EXISTS_BY_USER_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM commerce.purchase_order o WHERE o.user_id = :userId)";

    String EXISTS_FULFILLED_BY_USER_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM commerce.purchase_order o WHERE o.user_id = :userId AND o.state = 'FULFILLED')";

    Optional<PurchaseOrder> findByCode(String code);

    Optional<PurchaseOrder> findByCodeAndUserId(String code, UUID userId);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.code = :code")
    Optional<PurchaseOrder> lockByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.userId = :userId AND o.productCode = :productCode AND o.state = 'PENDING_PAYMENT'")
    Optional<PurchaseOrder> lockPendingByUserAndProduct(
            @Param("userId") UUID userId, @Param("productCode") String productCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id IN :ids ORDER BY o.id ASC")
    List<PurchaseOrder> lockAllByIdInOrderByIdAsc(@Param("ids") List<UUID> ids);

    Page<PurchaseOrder> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    @Query(value = EXISTS_BY_USER_ID_SQL, nativeQuery = true)
    boolean existsByUserId(@Param("userId") UUID userId);

    @Query(value = EXISTS_FULFILLED_BY_USER_ID_SQL, nativeQuery = true)
    boolean existsFulfilledByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT o.id FROM PurchaseOrder o
            WHERE o.state = :state AND o.paidAt >= :cutoff
            ORDER BY o.paidAt ASC, o.createdAt ASC
            """)
    List<UUID> findPaidIdsSince(
            @Param("state") OrderState state, @Param("cutoff") Instant cutoff, Pageable pageable);

    @Query(value = """
            SELECT o.code, i.exam_id
            FROM commerce.purchase_order o
            JOIN commerce.purchase_order_item i ON i.order_id = o.id
            WHERE o.user_id = :userId AND o.state = 'PAID'
            ORDER BY o.paid_at ASC NULLS LAST, o.created_at ASC, i.position ASC
            """, nativeQuery = true)
    List<Object[]> findPaidUnfulfilledExamRows(@Param("userId") UUID userId);

    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT id, state, paid_at, replaced_by_order_id
                FROM commerce.purchase_order
                WHERE id = :startId
                UNION ALL
                SELECT nxt.id, nxt.state, nxt.paid_at, nxt.replaced_by_order_id
                FROM commerce.purchase_order nxt
                JOIN chain c ON nxt.id = c.replaced_by_order_id
            )
            SELECT EXISTS (
                SELECT 1 FROM chain
                WHERE paid_at IS NOT NULL OR state IN ('PAID', 'FULFILLED', 'REFUNDED')
            )
            """, nativeQuery = true)
    boolean existsPaidInReplacementChainStartingAt(@Param("startId") UUID startId);

    @Query(value = """
            WITH RECURSIVE predecessors AS (
                SELECT id, state, paid_at
                FROM commerce.purchase_order
                WHERE replaced_by_order_id = :orderId
                UNION ALL
                SELECT prev.id, prev.state, prev.paid_at
                FROM commerce.purchase_order prev
                JOIN predecessors p ON prev.replaced_by_order_id = p.id
            )
            SELECT id FROM predecessors
            WHERE paid_at IS NOT NULL OR state IN ('PAID', 'FULFILLED', 'REFUNDED')
            ORDER BY id ASC
            """, nativeQuery = true)
    List<UUID> findPaidPredecessorOrderIds(@Param("orderId") UUID orderId);
}
