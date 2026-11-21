package vn.unicom.thi360.commerce.payment.vnpay;

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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;

/**
 * Nhận việc tra cứu {@code querydr} theo cơ chế {@code FOR UPDATE SKIP LOCKED} và cập nhật
 * {@code next_query_at} trong transaction ngắn {@code REQUIRES_NEW} để khóa được giải phóng
 * trước khi gọi HTTP ra ngoài (plan B2, AC-15, AC-16).
 */
@Repository
public class QueryDrClaimRepository {

    public record ClaimedPayment(
            UUID paymentId,
            UUID orderId,
            String orderCode,
            String txnRef,
            String vnpCreateDate,
            String vnpExpireDate,
            long amountVnd,
            String vnpTransactionNo,
            PaymentState stateBefore
    ) {
    }

    private static final String CLAIM_WORKER_SQL = """
            WITH picked AS (
                SELECT p.id, p.next_query_at AS prev_next_query_at, p.created_at
                FROM commerce.payment p
                WHERE p.state IN ('CREATED', 'UNKNOWN')
                  AND p.created_at <= ?
                  AND p.created_at >= ?
                  AND (p.next_query_at IS NULL OR p.next_query_at <= ?)
                ORDER BY p.next_query_at ASC NULLS FIRST, p.created_at ASC, p.id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            ),
            claimed AS (
                UPDATE commerce.payment p
                SET next_query_at = ?, updated_at = ?, version = p.version + 1
                FROM picked
                WHERE p.id = picked.id
                RETURNING p.id, p.order_id, p.txn_ref, p.vnp_create_date, p.vnp_expire_date,
                          p.amount_vnd, p.vnp_transaction_no, p.state,
                          picked.prev_next_query_at, picked.created_at
            )
            SELECT c.id AS payment_id,
                   c.order_id,
                   o.code AS order_code,
                   c.txn_ref,
                   c.vnp_create_date,
                   c.vnp_expire_date,
                   c.amount_vnd,
                   c.vnp_transaction_no,
                   c.state AS state_before
            FROM claimed c
            JOIN commerce.purchase_order o ON o.id = c.order_id
            ORDER BY c.prev_next_query_at ASC NULLS FIRST, c.created_at ASC, c.id ASC
            """;

    private static final String CLAIM_FOR_ORDER_SQL = """
            WITH picked AS (
                SELECT p.id, p.attempt_seq
                FROM commerce.payment p
                WHERE p.order_id = ?
                  AND p.state IN ('CREATED', 'UNKNOWN')
                  AND p.created_at >= ?
                  AND (p.next_query_at IS NULL OR p.next_query_at <= ?)
                ORDER BY p.attempt_seq ASC, p.id ASC
                FOR UPDATE SKIP LOCKED
            ),
            claimed AS (
                UPDATE commerce.payment p
                SET next_query_at = ?, updated_at = ?, version = p.version + 1
                FROM picked
                WHERE p.id = picked.id
                RETURNING p.id, p.order_id, p.txn_ref, p.vnp_create_date, p.vnp_expire_date,
                          p.amount_vnd, p.vnp_transaction_no, p.state, picked.attempt_seq
            )
            SELECT c.id AS payment_id,
                   c.order_id,
                   o.code AS order_code,
                   c.txn_ref,
                   c.vnp_create_date,
                   c.vnp_expire_date,
                   c.amount_vnd,
                   c.vnp_transaction_no,
                   c.state AS state_before
            FROM claimed c
            JOIN commerce.purchase_order o ON o.id = c.order_id
            ORDER BY c.attempt_seq ASC, c.id ASC
            """;

    private static final String TRANSITION_AGED_PAYMENTS_SQL = """
            WITH aged AS (
                SELECT id
                FROM commerce.payment
                WHERE created_at < ?
                  AND (state = 'CREATED' OR (state = 'UNKNOWN' AND next_query_at IS NOT NULL))
                ORDER BY order_id ASC, attempt_seq ASC, id ASC
                FOR UPDATE SKIP LOCKED
            )
            UPDATE commerce.payment p
            SET state = 'UNKNOWN', next_query_at = NULL, updated_at = ?, version = p.version + 1
            FROM aged
            WHERE p.id = aged.id
            """;

    private static final RowMapper<ClaimedPayment> ROW_MAPPER = QueryDrClaimRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;

    public QueryDrClaimRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        TransactionTemplate tx = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    /**
     * Nhận tối đa {@code batchSize} lần thanh toán mở trong cửa sổ {@code [minAge, maxAge]}
     * và đặt {@code next_query_at = now + cooldown} rồi commit trước khi trả về.
     */
    public List<ClaimedPayment> claimEligibleForWorker(
            Instant now, Duration minAge, Duration maxAge, Duration cooldown, int batchSize) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(minAge, "minAge must not be null");
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        Objects.requireNonNull(cooldown, "cooldown must not be null");
        if (batchSize <= 0) {
            return List.of();
        }
        Instant minCutoff = now.minus(minAge);
        Instant maxCutoff = now.minus(maxAge);
        Instant nextQueryAt = now.plus(cooldown);

        List<ClaimedPayment> result = requiresNewTx.execute(status -> jdbcTemplate.query(
                CLAIM_WORKER_SQL,
                (PreparedStatement ps) -> {
                    ps.setObject(1, utc(minCutoff));
                    ps.setObject(2, utc(maxCutoff));
                    ps.setObject(3, utc(now));
                    ps.setInt(4, batchSize);
                    ps.setObject(5, utc(nextQueryAt));
                    ps.setObject(6, utc(now));
                },
                ROW_MAPPER));
        return result == null ? List.of() : result;
    }

    /**
     * Nhận toàn bộ lần thanh toán mở trong cửa sổ {@code maxAge} chưa quá cooldown của một đơn
     * (dùng cho worker hết hạn đơn) và đặt {@code next_query_at = now + cooldown} trong {@code REQUIRES_NEW}.
     */
    public List<ClaimedPayment> claimOpenForOrder(
            UUID orderId, Instant now, Duration maxAge, Duration cooldown) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        Objects.requireNonNull(cooldown, "cooldown must not be null");
        Instant maxCutoff = now.minus(maxAge);
        Instant nextQueryAt = now.plus(cooldown);

        List<ClaimedPayment> result = requiresNewTx.execute(status -> jdbcTemplate.query(
                CLAIM_FOR_ORDER_SQL,
                (PreparedStatement ps) -> {
                    ps.setObject(1, orderId);
                    ps.setObject(2, utc(maxCutoff));
                    ps.setObject(3, utc(now));
                    ps.setObject(4, utc(nextQueryAt));
                    ps.setObject(5, utc(now));
                },
                ROW_MAPPER));
        return result == null ? List.of() : result;
    }

    /**
     * Chuyển các lần thanh toán {@code CREATED} đã quá ngưỡng {@code maxAge} sang {@code UNKNOWN}
     * và xóa {@code next_query_at} cho các lần mở đã quá 24 giờ trong transaction {@code REQUIRES_NEW}.
     */
    public int transitionAgedPaymentsPastMaxAge(Instant cutoff, Instant now) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        Objects.requireNonNull(now, "now must not be null");
        OffsetDateTime cutoffUtc = utc(cutoff);
        OffsetDateTime nowUtc = utc(now);
        Integer updated = requiresNewTx.execute(status -> jdbcTemplate.update(
                TRANSITION_AGED_PAYMENTS_SQL, cutoffUtc, nowUtc));
        return updated == null ? 0 : updated;
    }

    /**
     * Đẩy {@code next_query_at = now + cooldown} cho lần thanh toán còn mở khi tra cứu trực tiếp
     * để tránh worker nền tra cứu trùng lặp ngay sau đó.
     */
    public void extendCooldownIfOpen(UUID paymentId, Instant now, Duration cooldown) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(cooldown, "cooldown must not be null");
        OffsetDateTime nextQueryAtUtc = utc(now.plus(cooldown));
        OffsetDateTime nowUtc = utc(now);
        requiresNewTx.executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE commerce.payment
                SET next_query_at = ?, updated_at = ?, version = version + 1
                WHERE id = ?
                  AND state IN ('CREATED', 'UNKNOWN')
                """, nextQueryAtUtc, nowUtc, paymentId));
    }

    private static ClaimedPayment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ClaimedPayment(
                rs.getObject("payment_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_code"),
                rs.getString("txn_ref"),
                rs.getString("vnp_create_date"),
                rs.getString("vnp_expire_date"),
                rs.getLong("amount_vnd"),
                rs.getString("vnp_transaction_no"),
                PaymentState.valueOf(rs.getString("state_before")));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
