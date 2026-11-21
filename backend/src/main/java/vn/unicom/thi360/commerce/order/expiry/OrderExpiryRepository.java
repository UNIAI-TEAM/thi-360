package vn.unicom.thi360.commerce.order.expiry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    /** Tìm tối đa {@code batchSize} đơn {@code PENDING_PAYMENT} đã quá {@code expires_at}. */
    public List<UUID> findExpiredPendingOrderIds(Instant now, Collection<UUID> excludedIds, int batchSize) {
        Objects.requireNonNull(now, "now must not be null");
        if (batchSize <= 0) {
            return List.of();
        }
        OffsetDateTime cutoff = utc(now);
        if (excludedIds == null || excludedIds.isEmpty()) {
            return jdbcTemplate.query(
                    """
                    SELECT id
                    FROM commerce.purchase_order
                    WHERE state = 'PENDING_PAYMENT'
                      AND expires_at IS NOT NULL
                      AND expires_at <= ?
                    ORDER BY expires_at ASC, id ASC
                    LIMIT ?
                    """,
                    (PreparedStatement ps) -> {
                        ps.setObject(1, cutoff);
                        ps.setInt(2, batchSize);
                    },
                    (rs, rowNum) -> rs.getObject("id", UUID.class));
        }

        List<UUID> excluded = new ArrayList<>(excludedIds);
        String placeholders = String.join(", ", Collections.nCopies(excluded.size(), "?"));
        String sql = """
                SELECT id
                FROM commerce.purchase_order
                WHERE state = 'PENDING_PAYMENT'
                  AND expires_at IS NOT NULL
                  AND expires_at <= ?
                  AND id NOT IN (""" + placeholders + """
                )
                ORDER BY expires_at ASC, id ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (PreparedStatement ps) -> {
                    int idx = 1;
                    ps.setObject(idx++, cutoff);
                    for (UUID id : excluded) {
                        ps.setObject(idx++, id);
                    }
                    ps.setInt(idx, batchSize);
                },
                (rs, rowNum) -> rs.getObject("id", UUID.class));
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
