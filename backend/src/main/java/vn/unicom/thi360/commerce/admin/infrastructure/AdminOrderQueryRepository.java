package vn.unicom.thi360.commerce.admin.infrastructure;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.GrantView;
import vn.unicom.thi360.commerce.admin.api.AdminOrderSummaryItem;
import vn.unicom.thi360.commerce.admin.api.ExamBuyerOrderRef;
import vn.unicom.thi360.commerce.admin.api.OrderAttentionSummary;
import vn.unicom.thi360.commerce.admin.domain.AdminPaymentStatus;
import vn.unicom.thi360.commerce.admin.domain.AdminRefundStatus;
import vn.unicom.thi360.commerce.admin.domain.OrderAttentionType;
import vn.unicom.thi360.commerce.admin.domain.OrderStatusDeriver.PaymentSnapshot;
import vn.unicom.thi360.commerce.admin.domain.OrderStatusDeriver.RefundSnapshot;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.refund.domain.RefundState;
import vn.unicom.thi360.shared.api.PageQuery;

/**
 * Truy vấn đọc chuyên biệt cho màn quản trị đơn hàng, dải "Cần xử lý", chi tiết đơn và người mua (TM 9.1–9.3, BS §14).
 */
@Repository
public class AdminOrderQueryRepository {

    private static final Map<String, String> ORDER_SORT_COLUMNS = Map.of(
            "createdAt", "o.created_at",
            "paidAt", "o.paid_at",
            "fulfilledAt", "o.fulfilled_at",
            "amountDueVnd", "o.amount_due_vnd",
            "code", "o.code",
            "state", "o.state");

    private static final Map<String, String> BUYER_SORT_COLUMNS = Map.of(
            "lastPaidAt", "MAX(o.paid_at)",
            "userId", "o.user_id",
            "purchaseCount", "COUNT(DISTINCT o.id)",
            "username", "(ARRAY_AGG(o.buyer_username ORDER BY o.paid_at DESC NULLS LAST, o.created_at DESC, o.id DESC))[1]");

    private static final String PAYMENT_STATUS_SQL = """
            CASE
                WHEN EXISTS (SELECT 1 FROM commerce.payment ps WHERE ps.order_id = o.id AND ps.state = 'SUCCEEDED')
                    THEN 'SUCCEEDED'
                WHEN EXISTS (SELECT 1 FROM commerce.payment pp WHERE pp.order_id = o.id AND pp.state IN ('CREATED', 'UNKNOWN'))
                    THEN 'PENDING'
                WHEN EXISTS (SELECT 1 FROM commerce.payment pf WHERE pf.order_id = o.id)
                    THEN 'FAILED'
                WHEN o.payment_method = 'FREE'
                    THEN 'FREE'
                ELSE 'PENDING'
            END
            """;

    private static final String REFUND_STATUS_SQL = """
            CASE
                WHEN EXISTS (
                    SELECT 1 FROM commerce.refund ri
                    WHERE ri.order_id = o.id AND ri.state IN ('PROCESSING', 'UNKNOWN')
                ) THEN 'IN_PROGRESS'
                WHEN COALESCE((SELECT SUM(ps.amount_vnd) FROM commerce.payment ps WHERE ps.order_id = o.id AND ps.state = 'SUCCEEDED'), 0) > 0
                     AND COALESCE((SELECT SUM(rs.amount_vnd) FROM commerce.refund rs WHERE rs.order_id = o.id AND rs.state = 'SUCCEEDED'), 0)
                         >= COALESCE((SELECT SUM(ps.amount_vnd) FROM commerce.payment ps WHERE ps.order_id = o.id AND ps.state = 'SUCCEEDED'), 0)
                    THEN 'FULL'
                WHEN COALESCE((SELECT SUM(rs.amount_vnd) FROM commerce.refund rs WHERE rs.order_id = o.id AND rs.state = 'SUCCEEDED'), 0) > 0
                    THEN 'PARTIAL'
                ELSE 'NONE'
            END
            """;

    private static final String ATTN_LATE_SUCCESS_PENDING_SQL =
            "EXISTS (SELECT 1 FROM commerce.payment p WHERE p.order_id = o.id AND p.late_success_review = 'PENDING')";

    private static final String ATTN_PAID_UNFULFILLED_SQL =
            "o.state = 'PAID'";

    private static final String ATTN_UNKNOWN_OVER_24H_SQL =
            "EXISTS (SELECT 1 FROM commerce.payment p WHERE p.order_id = o.id AND p.state = 'UNKNOWN' AND p.created_at < :unknownThreshold24h)";

    private static final String ATTN_DUPLICATE_UNREFUNDED_SQL = """
            EXISTS (
                SELECT 1 FROM commerce.payment p
                WHERE p.order_id = o.id
                  AND p.state = 'SUCCEEDED'
                  AND p.is_duplicate = true
                  AND p.amount_vnd > COALESCE((
                      SELECT SUM(r.amount_vnd) FROM commerce.refund r
                      WHERE r.payment_id = p.id AND r.state = 'SUCCEEDED'
                  ), 0)
            )
            """;

    private static final String ATTN_NEEDS_REVIEW_SQL =
            "o.needs_review = true";

    private static final String ATTN_REFUND_UNKNOWN_SQL =
            "EXISTS (SELECT 1 FROM commerce.refund r WHERE r.order_id = o.id AND r.state = 'UNKNOWN')";

    private final NamedParameterJdbcTemplate jdbc;

    public AdminOrderQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Optional<UUID> findOrderIdByCode(String code) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM commerce.purchase_order WHERE code = :code",
                new MapSqlParameterSource("code", code),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        return ids.stream().findFirst();
    }

    public OrderAttentionSummary getAttentionSummary(Instant unknownThreshold24h) {
        String sql = """
                WITH attn AS (
                    SELECT DISTINCT p.order_id, 1 AS bucket
                    FROM commerce.payment p
                    WHERE p.late_success_review = 'PENDING'
                    UNION ALL
                    SELECT o.id AS order_id, 2 AS bucket
                    FROM commerce.purchase_order o
                    WHERE o.state = 'PAID'
                    UNION ALL
                    SELECT DISTINCT p.order_id, 3 AS bucket
                    FROM commerce.payment p
                    WHERE p.state = 'UNKNOWN' AND p.created_at < :unknownThreshold24h
                    UNION ALL
                    SELECT DISTINCT p.order_id, 4 AS bucket
                    FROM commerce.payment p
                    WHERE p.state = 'SUCCEEDED'
                      AND p.is_duplicate = true
                      AND p.amount_vnd > COALESCE((
                          SELECT SUM(r.amount_vnd)
                          FROM commerce.refund r
                          WHERE r.payment_id = p.id AND r.state = 'SUCCEEDED'
                      ), 0)
                    UNION ALL
                    SELECT o.id AS order_id, 5 AS bucket
                    FROM commerce.purchase_order o
                    WHERE o.needs_review = true
                    UNION ALL
                    SELECT DISTINCT r.order_id, 6 AS bucket
                    FROM commerce.refund r
                    WHERE r.state = 'UNKNOWN'
                )
                SELECT
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 1) AS late_success_pending,
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 2) AS paid_unfulfilled,
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 3) AS payment_unknown_over_24h,
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 4) AS duplicate_unrefunded,
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 5) AS needs_review,
                    COUNT(DISTINCT order_id) FILTER (WHERE bucket = 6) AS refund_unknown,
                    COUNT(DISTINCT order_id) AS total_attention_orders
                FROM attn
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unknownThreshold24h", Timestamp.from(unknownThreshold24h));
        return jdbc.queryForObject(sql, params, (rs, rowNum) -> new OrderAttentionSummary(
                rs.getLong("late_success_pending"),
                rs.getLong("paid_unfulfilled"),
                rs.getLong("payment_unknown_over_24h"),
                rs.getLong("duplicate_unrefunded"),
                rs.getLong("needs_review"),
                rs.getLong("refund_unknown"),
                rs.getLong("total_attention_orders")));
    }

    public PagedOrderRows findOrders(OrderFilter filter, Instant unknownThreshold24h, PageQuery pageQuery) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unknownThreshold24h", Timestamp.from(unknownThreshold24h));
        String whereClause = buildWhereClause(filter, params);

        Long totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order o" + whereClause,
                params,
                Long.class);
        long total = totalCount == null ? 0L : totalCount;
        if (total == 0L) {
            return new PagedOrderRows(List.of(), 0L);
        }

        String orderBy = buildOrderBy(pageQuery.sort());
        params.addValue("limit", pageQuery.size());
        params.addValue("offset", (long) pageQuery.page() * pageQuery.size());

        String sql = """
                SELECT o.id, o.code, o.user_id, o.buyer_username, o.buyer_full_name, o.product_type,
                       o.exam_id, o.bundle_id, o.product_code, o.product_name, o.list_price_vnd, o.amount_due_vnd,
                       o.payment_method, o.state, o.needs_review, o.created_at, o.paid_at, o.fulfilled_at
                FROM commerce.purchase_order o%s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(whereClause, orderBy);

        List<OrderRow> rows = jdbc.query(sql, params, (rs, rowNum) -> new OrderRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getObject("user_id", UUID.class),
                rs.getString("buyer_username"),
                rs.getString("buyer_full_name"),
                ProductType.valueOf(rs.getString("product_type")),
                rs.getObject("exam_id", UUID.class),
                rs.getObject("bundle_id", UUID.class),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getLong("list_price_vnd"),
                rs.getLong("amount_due_vnd"),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                OrderState.valueOf(rs.getString("state")),
                rs.getBoolean("needs_review"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("paid_at")),
                toInstant(rs.getTimestamp("fulfilled_at"))));
        return new PagedOrderRows(rows, total);
    }

    public Map<UUID, List<AdminOrderSummaryItem>> findOrderSummaryItemsByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT order_id, exam_id, exam_code, exam_name, position
                FROM commerce.purchase_order_item
                WHERE order_id IN (:orderIds)
                ORDER BY order_id ASC, position ASC, id ASC
                """;
        Map<UUID, List<AdminOrderSummaryItem>> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("orderIds", orderIds), rs -> {
            UUID orderId = rs.getObject("order_id", UUID.class);
            AdminOrderSummaryItem item = new AdminOrderSummaryItem(
                    rs.getObject("exam_id", UUID.class),
                    rs.getString("exam_code"),
                    rs.getString("exam_name"),
                    rs.getInt("position"));
            result.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(item);
        });
        return result;
    }

    public Map<UUID, List<PaymentSnapshot>> findPaymentSnapshotsByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT p.order_id, p.state, p.is_duplicate, p.late_success_review, p.amount_vnd, p.created_at,
                       COALESCE(SUM(r.amount_vnd) FILTER (WHERE r.state = 'SUCCEEDED'), 0) AS succeeded_refunded_vnd
                FROM commerce.payment p
                LEFT JOIN commerce.refund r ON r.payment_id = p.id
                WHERE p.order_id IN (:orderIds)
                GROUP BY p.id, p.order_id, p.state, p.is_duplicate, p.late_success_review, p.amount_vnd, p.created_at
                ORDER BY p.attempt_seq ASC
                """;
        Map<UUID, List<PaymentSnapshot>> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("orderIds", orderIds), rs -> {
            UUID orderId = rs.getObject("order_id", UUID.class);
            String rawLateReview = rs.getString("late_success_review");
            LateSuccessReview lateReview = rawLateReview == null ? null : LateSuccessReview.valueOf(rawLateReview);
            PaymentSnapshot snap = new PaymentSnapshot(
                    PaymentState.valueOf(rs.getString("state")),
                    rs.getBoolean("is_duplicate"),
                    lateReview,
                    rs.getLong("amount_vnd"),
                    rs.getLong("succeeded_refunded_vnd"),
                    toInstant(rs.getTimestamp("created_at")));
            result.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(snap);
        });
        return result;
    }

    public Map<UUID, List<RefundSnapshot>> findRefundSnapshotsByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT r.order_id, r.state, r.amount_vnd
                FROM commerce.refund r
                WHERE r.order_id IN (:orderIds)
                ORDER BY r.requested_at DESC
                """;
        Map<UUID, List<RefundSnapshot>> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("orderIds", orderIds), rs -> {
            UUID orderId = rs.getObject("order_id", UUID.class);
            RefundSnapshot snap = new RefundSnapshot(
                    RefundState.valueOf(rs.getString("state")),
                    rs.getLong("amount_vnd"));
            result.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(snap);
        });
        return result;
    }

    public List<GrantView> findGrantsByOrderId(UUID orderId) {
        String sql = """
                SELECT id, entitlement_id, exam_id, attempts, attempts_used, attempts_reserved, attempts_revoked,
                       (attempts - attempts_used - attempts_reserved - attempts_revoked) AS attempts_left,
                       revoked_at, granted_at, granted_by
                FROM commerce.entitlement_grant
                WHERE order_id = :orderId
                ORDER BY seq ASC, id ASC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("orderId", orderId), (rs, rowNum) -> new GrantView(
                rs.getObject("id", UUID.class),
                rs.getObject("entitlement_id", UUID.class),
                rs.getObject("exam_id", UUID.class),
                rs.getInt("attempts"),
                rs.getInt("attempts_used"),
                rs.getInt("attempts_reserved"),
                rs.getInt("attempts_revoked"),
                rs.getInt("attempts_left"),
                toInstant(rs.getTimestamp("revoked_at")),
                toInstant(rs.getTimestamp("granted_at")),
                rs.getString("granted_by")));
    }

    public PagedBuyerRows findExamBuyers(UUID examId, PageQuery pageQuery) {
        MapSqlParameterSource params = new MapSqlParameterSource("examId", examId);
        Long totalCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT o.user_id)
                FROM commerce.purchase_order_item i
                JOIN commerce.purchase_order o ON o.id = i.order_id
                WHERE i.exam_id = :examId AND o.state IN ('PAID', 'FULFILLED')
                """, params, Long.class);
        long total = totalCount == null ? 0L : totalCount;
        if (total == 0L) {
            return new PagedBuyerRows(List.of(), 0L);
        }

        String orderBy = buildBuyerOrderBy(pageQuery.sort());
        params.addValue("limit", pageQuery.size());
        params.addValue("offset", (long) pageQuery.page() * pageQuery.size());

        String sql = """
                SELECT o.user_id,
                       (ARRAY_AGG(o.buyer_username ORDER BY o.paid_at DESC NULLS LAST, o.created_at DESC, o.id DESC))[1] AS username,
                       (ARRAY_AGG(o.buyer_full_name ORDER BY o.paid_at DESC NULLS LAST, o.created_at DESC, o.id DESC))[1] AS full_name,
                       COUNT(DISTINCT o.id)::int AS purchase_count,
                       MAX(o.paid_at) AS last_paid_at
                FROM commerce.purchase_order_item i
                JOIN commerce.purchase_order o ON o.id = i.order_id
                WHERE i.exam_id = :examId AND o.state IN ('PAID', 'FULFILLED')
                GROUP BY o.user_id
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(orderBy);

        List<BuyerSummaryRow> rows = jdbc.query(sql, params, (rs, rowNum) -> new BuyerSummaryRow(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getInt("purchase_count"),
                toInstant(rs.getTimestamp("last_paid_at"))));
        return new PagedBuyerRows(rows, total);
    }

    public Map<UUID, List<ExamBuyerOrderRef>> findQualifyingOrdersByExamAndUsers(
            UUID examId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT DISTINCT o.id, o.user_id, o.code, o.state, o.amount_due_vnd, o.paid_at, o.created_at
                FROM commerce.purchase_order o
                JOIN commerce.purchase_order_item i ON i.order_id = o.id
                WHERE i.exam_id = :examId
                  AND o.user_id IN (:userIds)
                  AND o.state IN ('PAID', 'FULFILLED')
                ORDER BY o.paid_at DESC NULLS LAST, o.created_at DESC, o.id DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("userIds", userIds);
        Map<UUID, List<ExamBuyerOrderRef>> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            UUID userId = rs.getObject("user_id", UUID.class);
            ExamBuyerOrderRef ref = new ExamBuyerOrderRef(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    OrderState.valueOf(rs.getString("state")),
                    rs.getLong("amount_due_vnd"),
                    toInstant(rs.getTimestamp("paid_at")),
                    toInstant(rs.getTimestamp("created_at")));
            result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(ref);
        });
        return result;
    }

    public Map<UUID, BuyerEntitlementCounters> findPurchaseEntitlementsByExamAndUsers(
            UUID examId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT user_id, attempts_total, attempts_used, attempts_reserved, attempts_revoked
                FROM commerce.entitlement
                WHERE exam_id = :examId
                  AND user_id IN (:userIds)
                  AND source = 'PURCHASE'
                  AND source_ref IS NULL
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("examId", examId)
                .addValue("userIds", userIds);
        Map<UUID, BuyerEntitlementCounters> result = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            UUID userId = rs.getObject("user_id", UUID.class);
            int total = rs.getInt("attempts_total");
            int used = rs.getInt("attempts_used");
            int reserved = rs.getInt("attempts_reserved");
            int revoked = rs.getInt("attempts_revoked");
            int remaining = Math.max(0, total - used - reserved - revoked);
            result.put(userId, new BuyerEntitlementCounters(total, used, reserved, revoked, remaining));
        });
        return result;
    }

    private String buildWhereClause(OrderFilter filter, MapSqlParameterSource params) {
        if (filter == null) {
            return "";
        }
        List<String> predicates = new ArrayList<>();
        if (StringUtils.hasText(filter.q())) {
            String pattern = "%" + escapeLike(filter.q().trim()) + "%";
            predicates.add("""
                    (
                        o.code ILIKE :qPattern ESCAPE '\\'
                        OR o.buyer_username ILIKE :qPattern ESCAPE '\\'
                        OR EXISTS (
                            SELECT 1 FROM commerce.payment pq
                            WHERE pq.order_id = o.id
                              AND (pq.txn_ref ILIKE :qPattern ESCAPE '\\'
                                   OR COALESCE(pq.vnp_transaction_no, '') ILIKE :qPattern ESCAPE '\\')
                        )
                    )
                    """);
            params.addValue("qPattern", pattern);
        }
        if (filter.examId() != null) {
            predicates.add("""
                    EXISTS (
                        SELECT 1 FROM commerce.purchase_order_item poi
                        WHERE poi.order_id = o.id AND poi.exam_id = :examId
                    )
                    """);
            params.addValue("examId", filter.examId());
        }
        if (filter.from() != null) {
            predicates.add("o.created_at >= :fromTs");
            params.addValue("fromTs", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            predicates.add("o.created_at <= :toTs");
            params.addValue("toTs", Timestamp.from(filter.to()));
        }
        if (filter.paymentMethod() != null) {
            predicates.add("o.payment_method = :paymentMethod");
            params.addValue("paymentMethod", filter.paymentMethod().name());
        }
        if (filter.paymentStatus() != null) {
            predicates.add("(" + PAYMENT_STATUS_SQL + ") = :paymentStatus");
            params.addValue("paymentStatus", filter.paymentStatus().name());
        }
        if (filter.refundStatus() != null) {
            predicates.add("(" + REFUND_STATUS_SQL + ") = :refundStatus");
            params.addValue("refundStatus", filter.refundStatus().name());
        }
        if (filter.attention() != null) {
            predicates.add(attentionPredicate(filter.attention()));
        }
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private static String attentionPredicate(OrderAttentionType attention) {
        return switch (attention) {
            case LATE_SUCCESS_PENDING -> ATTN_LATE_SUCCESS_PENDING_SQL;
            case PAID_UNFULFILLED -> ATTN_PAID_UNFULFILLED_SQL;
            case PAYMENT_UNKNOWN_OVER_24H -> ATTN_UNKNOWN_OVER_24H_SQL;
            case DUPLICATE_UNREFUNDED -> ATTN_DUPLICATE_UNREFUNDED_SQL;
            case NEEDS_REVIEW -> ATTN_NEEDS_REVIEW_SQL;
            case REFUND_UNKNOWN -> ATTN_REFUND_UNKNOWN_SQL;
        };
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "o.created_at DESC, o.id DESC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = ORDER_SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new IllegalArgumentException("Unsupported order sort property: " + order.getProperty());
            }
            String nullsClause = ("paidAt".equals(order.getProperty()) || "fulfilledAt".equals(order.getProperty()))
                    ? " NULLS LAST"
                    : "";
            clauses.add(column + (order.isAscending() ? " ASC" : " DESC") + nullsClause);
        }
        clauses.add("o.id DESC");
        return String.join(", ", clauses);
    }

    private static String buildBuyerOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "MAX(o.paid_at) DESC NULLS LAST, o.user_id ASC";
        }
        List<String> clauses = new ArrayList<>();
        boolean hasUserId = false;
        for (Sort.Order order : sort) {
            if ("userId".equals(order.getProperty())) {
                hasUserId = true;
            }
            String expr = BUYER_SORT_COLUMNS.get(order.getProperty());
            if (expr == null) {
                throw new IllegalArgumentException("Unsupported buyer sort property: " + order.getProperty());
            }
            String nullsClause = "lastPaidAt".equals(order.getProperty()) ? " NULLS LAST" : "";
            clauses.add(expr + (order.isAscending() ? " ASC" : " DESC") + nullsClause);
        }
        if (!hasUserId) {
            clauses.add("o.user_id ASC");
        }
        return String.join(", ", clauses);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record OrderFilter(
            String q,
            UUID examId,
            Instant from,
            Instant to,
            AdminPaymentStatus paymentStatus,
            AdminRefundStatus refundStatus,
            PaymentMethod paymentMethod,
            OrderAttentionType attention
    ) {
    }

    public record OrderRow(
            UUID orderId,
            String code,
            UUID buyerUserId,
            String buyerUsername,
            String buyerFullName,
            ProductType productType,
            UUID examId,
            UUID bundleId,
            String productCode,
            String productName,
            long listPriceVnd,
            long amountDueVnd,
            PaymentMethod paymentMethod,
            OrderState orderState,
            boolean needsReview,
            Instant createdAt,
            Instant paidAt,
            Instant fulfilledAt
    ) {
        public OrderRow(
                UUID orderId,
                String code,
                UUID buyerUserId,
                String buyerUsername,
                String buyerFullName,
                ProductType productType,
                UUID examId,
                String productCode,
                String productName,
                long listPriceVnd,
                long amountDueVnd,
                PaymentMethod paymentMethod,
                OrderState orderState,
                boolean needsReview,
                Instant createdAt,
                Instant paidAt,
                Instant fulfilledAt) {
            this(orderId, code, buyerUserId, buyerUsername, buyerFullName, productType, examId, null,
                    productCode, productName, listPriceVnd, amountDueVnd, paymentMethod, orderState,
                    needsReview, createdAt, paidAt, fulfilledAt);
        }
    }

    public record PagedOrderRows(List<OrderRow> rows, long totalItems) {
    }

    public record BuyerSummaryRow(
            UUID userId,
            String username,
            String fullName,
            int purchaseCount,
            Instant lastPaidAt
    ) {
    }

    public record PagedBuyerRows(List<BuyerSummaryRow> rows, long totalItems) {
    }

    public record BuyerEntitlementCounters(
            int attemptsTotal,
            int attemptsUsed,
            int attemptsReserved,
            int attemptsRevoked,
            int attemptsRemaining
    ) {
        public static BuyerEntitlementCounters zero() {
            return new BuyerEntitlementCounters(0, 0, 0, 0, 0);
        }
    }
}
