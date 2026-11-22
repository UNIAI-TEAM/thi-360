package vn.unicom.thi360.commerce.reconciliation.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.reconciliation.api.LatestReconciliationReportResponse;
import vn.unicom.thi360.commerce.reconciliation.api.ReconciliationReportResponse;
import vn.unicom.thi360.commerce.reconciliation.domain.ReconciliationReport;
import vn.unicom.thi360.commerce.reconciliation.infrastructure.ReconciliationReportRepository;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Tạo báo cáo đối soát cuối ngày (TM 8.4, BS 14, plan B5):
 * khóa bằng {@code pg_try_advisory_xact_lock(36005002)} và bảo đảm idempotent theo từng ngày.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    public static final long ADVISORY_LOCK_KEY = 36005002L;

    private final ReconciliationReportRepository reportRepository;
    private final JdbcTemplate jdbc;
    private final ReconciliationProperties properties;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public ReconciliationService(
            ReconciliationReportRepository reportRepository,
            JdbcTemplate jdbc,
            ReconciliationProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.reportRepository = Objects.requireNonNull(reportRepository);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.properties = Objects.requireNonNull(properties);
        PlatformTransactionManager txMgr = Objects.requireNonNull(transactionManager);
        this.txTemplate = new TransactionTemplate(txMgr);
        TransactionTemplate ro = new TransactionTemplate(txMgr);
        ro.setReadOnly(true);
        this.readOnlyTx = ro;
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<ReconciliationReportResponse> generateDailyReport() {
        return txTemplate.execute(status -> {
            Boolean acquired = jdbc.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("Skipping daily reconciliation report: advisory lock {} is currently held", ADVISORY_LOCK_KEY);
                return Optional.empty();
            }

            Instant now = clock.instant();
            ZoneId zone = ZoneId.of(properties.zone());
            LocalDate reportDate = LocalDate.now(clock.withZone(zone));
            if (reportRepository.findByReportDate(reportDate).isPresent()) {
                return Optional.empty();
            }

            List<Map<String, Object>> paidUnfulfilled = queryPaidUnfulfilled();
            List<Map<String, Object>> unknownPayments = queryUnknownPayments();
            List<Map<String, Object>> unrefundedExcess = queryUnrefundedExcess();
            List<Map<String, Object>> pendingLateSuccess = queryPendingLateSuccess();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("paidUnfulfilledOrders", paidUnfulfilled);
            details.put("unknownPayments", unknownPayments);
            details.put("unrefundedExcessPayments", unrefundedExcess);
            details.put("pendingLateSuccessPayments", pendingLateSuccess);

            ReconciliationReport report = new ReconciliationReport(
                    Ids.newId(),
                    reportDate,
                    now,
                    paidUnfulfilled.size(),
                    unknownPayments.size(),
                    unrefundedExcess.size(),
                    pendingLateSuccess.size(),
                    details);
            ReconciliationReport saved = reportRepository.saveAndFlush(report);
            return Optional.of(ReconciliationReportResponse.from(saved));
        });
    }

    public LatestReconciliationReportResponse getLatestReport() {
        return readOnlyTx.execute(status -> reportRepository.findTopByOrderByReportDateDesc()
                .map(report -> new LatestReconciliationReportResponse(true, ReconciliationReportResponse.from(report)))
                .orElseGet(() -> new LatestReconciliationReportResponse(false, null)));
    }

    private List<Map<String, Object>> queryPaidUnfulfilled() {
        return jdbc.query("""
                SELECT o.id AS order_id, o.code AS order_code, o.amount_due_vnd, o.paid_at
                FROM commerce.purchase_order o
                WHERE o.state = 'PAID'
                ORDER BY o.paid_at ASC NULLS LAST, o.created_at ASC, o.id ASC
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", rs.getObject("order_id", UUID.class).toString());
            row.put("orderCode", rs.getString("order_code"));
            row.put("amountVnd", rs.getLong("amount_due_vnd"));
            Timestamp paidAt = rs.getTimestamp("paid_at");
            row.put("paidAt", paidAt == null ? null : paidAt.toInstant().toString());
            return row;
        });
    }

    private List<Map<String, Object>> queryUnknownPayments() {
        return jdbc.query("""
                SELECT o.id AS order_id, o.code AS order_code, p.id AS payment_id, p.txn_ref, p.amount_vnd, p.created_at
                FROM commerce.payment p
                JOIN commerce.purchase_order o ON o.id = p.order_id
                WHERE p.state = 'UNKNOWN'
                ORDER BY p.created_at ASC, p.id ASC
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", rs.getObject("order_id", UUID.class).toString());
            row.put("orderCode", rs.getString("order_code"));
            row.put("paymentId", rs.getObject("payment_id", UUID.class).toString());
            row.put("txnRef", rs.getString("txn_ref"));
            row.put("amountVnd", rs.getLong("amount_vnd"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            row.put("createdAt", createdAt == null ? null : createdAt.toInstant().toString());
            return row;
        });
    }

    private List<Map<String, Object>> queryUnrefundedExcess() {
        return jdbc.query("""
                SELECT o.id AS order_id, o.code AS order_code, p.id AS payment_id, p.txn_ref,
                       p.amount_vnd, (p.amount_vnd - COALESCE(r.refunded_vnd, 0)) AS remaining_vnd,
                       p.excess_reason
                FROM commerce.payment p
                JOIN commerce.purchase_order o ON o.id = p.order_id
                LEFT JOIN (
                    SELECT payment_id, COALESCE(SUM(amount_vnd), 0) AS refunded_vnd
                    FROM commerce.refund
                    WHERE state = 'SUCCEEDED'
                    GROUP BY payment_id
                ) r ON r.payment_id = p.id
                WHERE p.state = 'SUCCEEDED'
                  AND p.is_duplicate = true
                  AND p.amount_vnd > COALESCE(r.refunded_vnd, 0)
                ORDER BY p.created_at ASC, p.id ASC
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", rs.getObject("order_id", UUID.class).toString());
            row.put("orderCode", rs.getString("order_code"));
            row.put("paymentId", rs.getObject("payment_id", UUID.class).toString());
            row.put("txnRef", rs.getString("txn_ref"));
            row.put("amountVnd", rs.getLong("amount_vnd"));
            row.put("remainingVnd", rs.getLong("remaining_vnd"));
            row.put("excessReason", rs.getString("excess_reason"));
            return row;
        });
    }

    private List<Map<String, Object>> queryPendingLateSuccess() {
        return jdbc.query("""
                SELECT o.id AS order_id, o.code AS order_code, p.id AS payment_id, p.txn_ref,
                       p.amount_vnd, p.late_success_at
                FROM commerce.payment p
                JOIN commerce.purchase_order o ON o.id = p.order_id
                WHERE p.late_success_review = 'PENDING'
                ORDER BY p.late_success_at ASC NULLS LAST, p.created_at ASC, p.id ASC
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", rs.getObject("order_id", UUID.class).toString());
            row.put("orderCode", rs.getString("order_code"));
            row.put("paymentId", rs.getObject("payment_id", UUID.class).toString());
            row.put("txnRef", rs.getString("txn_ref"));
            row.put("amountVnd", rs.getLong("amount_vnd"));
            Timestamp lateSuccessAt = rs.getTimestamp("late_success_at");
            row.put("lateSuccessAt", lateSuccessAt == null ? null : lateSuccessAt.toInstant().toString());
            return row;
        });
    }
}
