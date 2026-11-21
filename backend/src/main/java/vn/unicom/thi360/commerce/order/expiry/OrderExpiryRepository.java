package vn.unicom.thi360.commerce.order.expiry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;

/**
 * Truy vấn và khóa đơn hàng quá hạn thanh toán bằng {@link JdbcTemplate} theo đúng thứ tự khóa
 * toàn cục {@code purchase_order -> payment} (không sửa thực thể hay repository của CM-3a).
 */
@Repository
public class OrderExpiryRepository {

    public record ExpiredOrderCandidate(UUID id, Instant expiresAt) {
    }

    public record LockedOrder(
            UUID id,
            String code,
            OrderState state,
            Instant expiresAt,
            UUID promotionId,
            UUID voucherId
    ) {
    }

    public record LockedPayment(
            UUID id,
            PaymentState state,
            LateSuccessReview lateSuccessReview
    ) {
    }

    private static final String LOCK_ORDER_SQL = """
            SELECT id, code, state, expires_at, promotion_id, voucher_id
            FROM commerce.purchase_order
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String LOCK_PAYMENTS_SQL = """
            SELECT id, state, late_success_review
            FROM commerce.payment
            WHERE order_id = ?
            ORDER BY attempt_seq ASC
            FOR UPDATE
            """;

    private static final String MARK_EXPIRED_SQL = """
            UPDATE commerce.purchase_order
            SET state = 'EXPIRED', updated_at = ?, version = version + 1
            WHERE id = ? AND state = 'PENDING_PAYMENT'
            """;

    private final JdbcTemplate jdbcTemplate;

    public OrderExpiryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Tìm tối đa {@code batchSize} đơn {@code PENDING_PAYMENT} đã quá {@code expires_at}
     * và hiện có thể xử lý được, phân trang keyset theo {@code (expires_at, id)}.
     */
    public List<ExpiredOrderCandidate> findExpiredPendingOrderIds(
            Instant now, Duration paymentMaxAge, ExpiredOrderCandidate cursor, int batchSize) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(paymentMaxAge, "paymentMaxAge must not be null");
        if (batchSize <= 0) {
            return List.of();
        }
        OffsetDateTime cutoff = utc(now);
        OffsetDateTime maxAgeCutoff = utc(now.minus(paymentMaxAge));
        if (cursor == null) {
            return jdbcTemplate.query(
                    """
                    SELECT o.id, o.expires_at
                    FROM commerce.purchase_order o
                    WHERE o.state = 'PENDING_PAYMENT'
                      AND o.expires_at IS NOT NULL
                      AND o.expires_at <= ?
                      AND NOT EXISTS (
                          SELECT 1 FROM commerce.payment p
                          WHERE p.order_id = o.id
                            AND (p.state = 'SUCCEEDED'
                                 OR p.late_success_review = 'PENDING'
                                 OR (p.state IN ('CREATED', 'UNKNOWN')
                                     AND (p.created_at < ? OR (p.next_query_at IS NOT NULL AND p.next_query_at > ?))))
                      )
                    ORDER BY o.expires_at ASC, o.id ASC
                    LIMIT ?
                    """,
                    (PreparedStatement ps) -> {
                        ps.setObject(1, cutoff);
                        ps.setObject(2, maxAgeCutoff);
                        ps.setObject(3, cutoff);
                        ps.setInt(4, batchSize);
                    },
                    OrderExpiryRepository::mapCandidate);
        }

        OffsetDateTime cursorExpiresAt = utc(cursor.expiresAt());
        return jdbcTemplate.query(
                """
                SELECT o.id, o.expires_at
                FROM commerce.purchase_order o
                WHERE o.state = 'PENDING_PAYMENT'
                  AND o.expires_at IS NOT NULL
                  AND o.expires_at <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM commerce.payment p
                      WHERE p.order_id = o.id
                        AND (p.state = 'SUCCEEDED'
                             OR p.late_success_review = 'PENDING'
                             OR (p.state IN ('CREATED', 'UNKNOWN')
                                 AND (p.created_at < ? OR (p.next_query_at IS NOT NULL AND p.next_query_at > ?))))
                  )
                  AND (o.expires_at > ? OR (o.expires_at = ? AND o.id > ?))
                ORDER BY o.expires_at ASC, o.id ASC
                LIMIT ?
                """,
                (PreparedStatement ps) -> {
                    ps.setObject(1, cutoff);
                    ps.setObject(2, maxAgeCutoff);
                    ps.setObject(3, cutoff);
                    ps.setObject(4, cursorExpiresAt);
                    ps.setObject(5, cursorExpiresAt);
                    ps.setObject(6, cursor.id());
                    ps.setInt(7, batchSize);
                },
                OrderExpiryRepository::mapCandidate);
    }

    private static ExpiredOrderCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new ExpiredOrderCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
    }

    public LockedOrder lockOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return jdbcTemplate.query(LOCK_ORDER_SQL, OrderExpiryRepository::mapLockedOrder, orderId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<LockedPayment> lockPayments(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return jdbcTemplate.query(LOCK_PAYMENTS_SQL, OrderExpiryRepository::mapLockedPayment, orderId);
    }

    public int markExpired(UUID orderId, Instant now) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return jdbcTemplate.update(
                MARK_EXPIRED_SQL,
                ps -> {
                    ps.setObject(1, utc(now));
                    ps.setObject(2, orderId);
                });
    }

    private static LockedOrder mapLockedOrder(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime expiresOd = rs.getObject("expires_at", OffsetDateTime.class);
        return new LockedOrder(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                OrderState.valueOf(rs.getString("state")),
                expiresOd == null ? null : expiresOd.toInstant(),
                rs.getObject("promotion_id", UUID.class),
                rs.getObject("voucher_id", UUID.class));
    }

    private static LockedPayment mapLockedPayment(ResultSet rs, int rowNum) throws SQLException {
        String rawReview = rs.getString("late_success_review");
        return new LockedPayment(
                rs.getObject("id", UUID.class),
                PaymentState.valueOf(rs.getString("state")),
                rawReview == null ? null : LateSuccessReview.valueOf(rawReview));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
