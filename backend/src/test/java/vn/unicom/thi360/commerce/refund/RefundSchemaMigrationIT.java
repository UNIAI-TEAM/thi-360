package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * AC-1: kiểm chứng migration {@code V202611210410__order_refund_and_reconciliation.sql} chạy được trên DB
 * đã có dữ liệu đơn hàng/thanh toán mà không làm thay đổi dữ liệu cũ, đồng thời thực thi đủ các ràng buộc
 * duy nhất ({@code uq_refund_vnp_request_id}, {@code uq_refund_idempotency_key},
 * {@code uq_refund_active_per_payment}, {@code uq_reconciliation_report_date}) và ràng buộc kiểm tra.
 */
class RefundSchemaMigrationIT extends AbstractIntegrationTest {

    private static final String REFUND_MIGRATION_VERSION = "202611210410";
    private static final List<String> SCHEMAS = List.of(
            "shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam", "commerce", "attempt");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesExistingOrdersAndPaymentsWithoutModificationAndEnforcesUniqueConstraints() {
        String dbName = "refund_mig_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + dbName);
        try {
            String url = jdbcUrlFor(dbName);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            String beforeVersion = versionBefore(url, REFUND_MIGRATION_VERSION);
            flyway(url, beforeVersion).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);
            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            String orderCode = "DHMIG" + shortToken().substring(0, 6);
            String productCode = "EXMIG" + shortToken().substring(0, 6);
            UUID paymentId = UUID.randomUUID();
            String txnRef = orderCode + "01";

            isolated.update("""
                    INSERT INTO commerce.purchase_order
                        (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                         product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd, attempts_granted,
                         result_view, payment_method, state, paid_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'mig.buyer', 'Migration Buyer', 'EXAM', ?, 1, ?, 'Exam Mig', 150000, 'NONE',
                            150000, 2, 'FULL_SOLUTION', 'VNPAY', 'FULFILLED', now(), now(), now(), 0)
                    """, orderId, orderCode, userId, examId, productCode);
            isolated.update("""
                    INSERT INTO commerce.payment
                        (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd,
                         state, is_duplicate, vnp_transaction_no, created_at, updated_at, version)
                    VALUES (?, ?, 1, ?, '20261121100000', '20261121101500', 150000, 'SUCCEEDED', false, '991100',
                            now(), now(), 0)
                    """, paymentId, orderId, txnRef);

            flyway(url, REFUND_MIGRATION_VERSION).migrate();

            List<String> newTables = isolated.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'commerce'
                      AND table_name IN ('refund', 'reconciliation_report')
                    ORDER BY table_name
                    """, String.class);
            assertThat(newTables).containsExactly("reconciliation_report", "refund");

            Map<String, Object> existingOrder = isolated.queryForMap(
                    "SELECT code, state, amount_due_vnd FROM commerce.purchase_order WHERE id = ?", orderId);
            assertThat(existingOrder.get("code")).isEqualTo(orderCode);
            assertThat(existingOrder.get("state")).isEqualTo("FULFILLED");
            assertThat(((Number) existingOrder.get("amount_due_vnd")).longValue()).isEqualTo(150_000L);

            Map<String, Object> existingPayment = isolated.queryForMap(
                    "SELECT txn_ref, state, amount_vnd, is_duplicate FROM commerce.payment WHERE id = ?", paymentId);
            assertThat(existingPayment.get("txn_ref")).isEqualTo(txnRef);
            assertThat(existingPayment.get("state")).isEqualTo("SUCCEEDED");
            assertThat(((Number) existingPayment.get("amount_vnd")).longValue()).isEqualTo(150_000L);
            assertThat(existingPayment.get("is_duplicate")).isEqualTo(false);

            // Ràng buộc uq_refund_vnp_request_id, uq_refund_idempotency_key, uq_refund_active_per_payment.
            String reqId1 = UUID.randomUUID().toString().replace("-", "");
            String reqId2 = UUID.randomUUID().toString().replace("-", "");
            String idem1 = "idem-" + shortToken();
            String idem2 = "idem-" + shortToken();

            isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 50000, 'API', 'PROCESSING', 'System fault', true, 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), reqId1, paymentId, orderId, idem1);

            // Trùng vnp_request_id bị từ chối.
            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 10000, 'API', 'FAILED', 'Dup req', true, 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), reqId1, paymentId, orderId, idem2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Trùng idempotency_key bị từ chối.
            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 10000, 'API', 'FAILED', 'Dup idem', true, 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), reqId2, paymentId, orderId, idem1))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Hai refund PROCESSING/UNKNOWN trên cùng payment bị uq_refund_active_per_payment từ chối.
            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 10000, 'API', 'UNKNOWN', 'Second active', true, 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), reqId2, paymentId, orderId, idem2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // verified_system_error = false bị từ chối; MANUAL không có external_ref bị từ chối.
            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 10000, 'API', 'FAILED', 'Unverified', false, 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), reqId2, paymentId, orderId, idem2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.refund
                        (id, vnp_request_id, payment_id, order_id, amount_vnd, kind, state, reason,
                         verified_system_error, external_ref, requested_by, requested_at, idempotency_key, created_at, updated_at)
                    VALUES (?, NULL, ?, ?, 10000, 'MANUAL', 'SUCCEEDED', 'Manual missing ref', true, '   ', 'admin', now(), ?, now(), now())
                    """, UUID.randomUUID(), paymentId, orderId, idem2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Ràng buộc uq_reconciliation_report_date.
            LocalDate reportDate = LocalDate.of(2026, 11, 21);
            isolated.update("""
                    INSERT INTO commerce.reconciliation_report
                        (id, report_date, generated_at, paid_unfulfilled_count, unknown_payment_count,
                         unrefunded_excess_count, pending_late_success_count, details, created_at)
                    VALUES (?, ?, now(), 1, 2, 3, 4, '{}'::jsonb, now())
                    """, UUID.randomUUID(), Date.valueOf(reportDate));
            assertThatThrownBy(() -> isolated.update("""
                    INSERT INTO commerce.reconciliation_report
                        (id, report_date, generated_at, paid_unfulfilled_count, unknown_payment_count,
                         unrefunded_excess_count, pending_late_success_count, details, created_at)
                    VALUES (?, ?, now(), 0, 0, 0, 0, '{}'::jsonb, now())
                    """, UUID.randomUUID(), Date.valueOf(reportDate)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }

    private static String jdbcUrlFor(String dbName) {
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + dbName);
    }

    private static String versionBefore(String url, String targetVersion) {
        MigrationVersion target = MigrationVersion.fromVersion(targetVersion);
        MigrationInfo[] all = flyway(url, null).info().all();
        return Arrays.stream(all)
                .map(MigrationInfo::getVersion)
                .filter(v -> v.compareTo(target) < 0)
                .max(MigrationVersion::compareTo)
                .orElseThrow()
                .getVersion();
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("shared")
                .schemas(SCHEMAS.toArray(String[]::new))
                .table("flyway_schema_history")
                .locations("classpath:db/migration")
                .placeholders(Map.of("auditAppRole", POSTGRES.getUsername()));
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
