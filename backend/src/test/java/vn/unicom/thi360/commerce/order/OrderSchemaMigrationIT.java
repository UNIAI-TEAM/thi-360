package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * AC-1 và AC-2: kiểm chứng migration {@code V202611210230__purchase_order_and_payment.sql}:
 * đủ 5 bảng, các chỉ mục duy nhất/từng phần, và ràng buộc khóa ngoại trên dữ liệu có sẵn.
 */
class OrderSchemaMigrationIT extends AbstractIntegrationTest {

    private static final String ORDER_MIGRATION_VERSION = "202611210230";
    private static final List<String> SCHEMAS = List.of(
            "shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam", "commerce", "attempt");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAllFiveTablesAndEnforcesUniqueConstraints() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'commerce'
                  AND table_name IN ('purchase_order', 'purchase_order_item', 'payment', 'payment_event', 'order_history')
                ORDER BY table_name
                """, String.class);
        assertThat(tables).containsExactly(
                "order_history", "payment", "payment_event", "purchase_order", "purchase_order_item");

        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        String productCode = "MIG-" + UUID.randomUUID().toString().substring(0, 8);
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        UUID cancelledOrder = UUID.randomUUID();

        try {
            insertOrder(jdbc, order1, "DHM1" + shortToken(), userId, examId, productCode, "PENDING_PAYMENT");
            // Đơn thứ hai PENDING_PAYMENT cùng (user_id, product_code) phải bị uq_purchase_order_pending_user_product từ chối.
            assertThatThrownBy(() ->
                    insertOrder(jdbc, order2, "DHM2" + shortToken(), userId, examId, productCode, "PENDING_PAYMENT"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // Đơn đã CANCELLED cùng (user_id, product_code) được phép tồn tại song song.
            insertOrder(jdbc, cancelledOrder, "DHM3" + shortToken(), userId, examId, productCode, "CANCELLED");

            // uq_purchase_order_item_order_exam
            jdbc.update("""
                    INSERT INTO commerce.purchase_order_item
                        (id, order_id, exam_id, exam_version_no, exam_code, exam_name, attempts_granted, result_view, position)
                    VALUES (?, ?, ?, 1, ?, 'Test Exam', 1, 'FULL_SOLUTION', 1)
                    """, UUID.randomUUID(), order1, examId, productCode);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO commerce.purchase_order_item
                        (id, order_id, exam_id, exam_version_no, exam_code, exam_name, attempts_granted, result_view, position)
                    VALUES (?, ?, ?, 1, ?, 'Test Exam', 1, 'FULL_SOLUTION', 2)
                    """, UUID.randomUUID(), order1, examId, productCode))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // uq_payment_txn_ref
            String sharedTxnRef = "TXN" + shortToken();
            UUID pay1 = UUID.randomUUID();
            insertPayment(jdbc, pay1, order1, 1, sharedTxnRef, "SUCCEEDED", false, "VNP-REAL-1");
            assertThatThrownBy(() ->
                    insertPayment(jdbc, UUID.randomUUID(), order1, 2, sharedTxnRef, "FAILED", false, null))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // uq_payment_counted_per_order: chỉ tối đa 1 khoản SUCCEEDED + is_duplicate = false mỗi đơn
            assertThatThrownBy(() ->
                    insertPayment(jdbc, UUID.randomUUID(), order1, 2, "TXN" + shortToken(), "SUCCEEDED", false, "VNP-REAL-2"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // SUCCEEDED + is_duplicate = true được phép
            insertPayment(jdbc, UUID.randomUUID(), order1, 2, "TXN" + shortToken(), "SUCCEEDED", true, "VNP-REAL-2");

            // uq_payment_vnp_transaction_no: trùng giá trị thật bị chặn, nhưng nhiều '0' / rỗng / NULL được phép
            assertThatThrownBy(() ->
                    insertPayment(jdbc, UUID.randomUUID(), order1, 3, "TXN" + shortToken(), "FAILED", false, "VNP-REAL-1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            insertPayment(jdbc, UUID.randomUUID(), order1, 3, "TXN" + shortToken(), "FAILED", false, "0");
            insertPayment(jdbc, UUID.randomUUID(), order1, 4, "TXN" + shortToken(), "FAILED", false, "0");
            insertPayment(jdbc, UUID.randomUUID(), order1, 5, "TXN" + shortToken(), "FAILED", false, "");
            insertPayment(jdbc, UUID.randomUUID(), order1, 6, "TXN" + shortToken(), "FAILED", false, null);
        } finally {
            jdbc.update("DELETE FROM commerce.payment WHERE order_id IN (?, ?)", order1, cancelledOrder);
            jdbc.update("DELETE FROM commerce.purchase_order_item WHERE order_id IN (?, ?)", order1, cancelledOrder);
            jdbc.update("DELETE FROM commerce.purchase_order WHERE id IN (?, ?)", order1, cancelledOrder);
        }
    }

    @Test
    void foreignKeyMigrationKeepsCompensationGrantsAndFailsCleanlyOnOrphanGrantOrRedemption() {
        String baseDb = "order_mig_ok_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + baseDb);
        try {
            String url = jdbcUrlFor(baseDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            String beforeVersion = versionBefore(url, ORDER_MIGRATION_VERSION);
            flyway(url, beforeVersion).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);
            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            UUID entId = UUID.randomUUID();
            UUID compGrantId = UUID.randomUUID();
            UUID compAttemptId = UUID.randomUUID();

            isolated.update("""
                    INSERT INTO commerce.entitlement
                        (id, user_id, exam_id, source, source_ref, attempts_total, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'PURCHASE', NULL, 1, now(), now(), 0)
                    """, entId, userId, examId);
            isolated.update("""
                    INSERT INTO commerce.entitlement_grant
                        (id, entitlement_id, kind, order_id, order_code, exam_id, compensation_attempt_id,
                         attempts, result_view, exam_version_no, granted_at, granted_by, reason)
                    VALUES (?, ?, 'COMPENSATION', NULL, NULL, ?, ?, 1, 'SCORE', 1, now(), 'admin', 'Loi he thong')
                    """, compGrantId, entId, examId, compAttemptId);

            // Chạy tiếp migration của CM-3a: dòng COMPENSATION (order_id IS NULL) phải còn nguyên vẹn.
            flyway(url, ORDER_MIGRATION_VERSION).migrate();

            Map<String, Object> survived = isolated.queryForMap(
                    "SELECT kind, order_id, attempts FROM commerce.entitlement_grant WHERE id = ?", compGrantId);
            assertThat(survived.get("kind")).isEqualTo("COMPENSATION");
            assertThat(survived.get("order_id")).isNull();
            assertThat(survived.get("attempts")).isEqualTo(1);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + baseDb + " WITH (FORCE)");
        }

        // Nhánh (b1): orphan order_id trong entitlement_grant -> migration dừng báo lỗi, dữ liệu cũ còn nguyên.
        String orphanGrantDb = "order_mig_og_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + orphanGrantDb);
        try {
            String url = jdbcUrlFor(orphanGrantDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, ORDER_MIGRATION_VERSION)).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);
            UUID entId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            UUID orphanGrantId = UUID.randomUUID();
            UUID missingOrderId = UUID.randomUUID();
            isolated.update("""
                    INSERT INTO commerce.entitlement
                        (id, user_id, exam_id, source, source_ref, attempts_total, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'PURCHASE', NULL, 1, now(), now(), 0)
                    """, entId, UUID.randomUUID(), examId);
            isolated.update("""
                    INSERT INTO commerce.entitlement_grant
                        (id, entitlement_id, kind, order_id, order_code, exam_id, compensation_attempt_id,
                         attempts, result_view, exam_version_no, granted_at, granted_by)
                    VALUES (?, ?, 'PURCHASE', ?, 'DH-ORPHAN', ?, NULL, 1, 'SCORE', 1, now(), 'SYSTEM')
                    """, orphanGrantId, entId, missingOrderId, examId);

            assertThatThrownBy(() -> flyway(url, ORDER_MIGRATION_VERSION).migrate())
                    .isInstanceOf(FlywayException.class);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.entitlement_grant WHERE id = ?", Long.class, orphanGrantId))
                    .isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + orphanGrantDb + " WITH (FORCE)");
        }

        // Nhánh (b2): orphan order_id trong promotion_redemption -> migration dừng báo lỗi, dữ liệu cũ còn nguyên.
        String orphanRedemptionDb = "order_mig_or_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + orphanRedemptionDb);
        try {
            String url = jdbcUrlFor(orphanRedemptionDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, ORDER_MIGRATION_VERSION)).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);
            UUID promoId = UUID.randomUUID();
            UUID redemptionId = UUID.randomUUID();
            isolated.update("""
                    INSERT INTO commerce.promotion
                        (id, kind, name, code, code_normalized, discount_type, discount_value, starts_at, ends_at,
                         combinable, scope, audience, status, created_at, updated_at)
                    VALUES (?, 'VOUCHER', 'Orphan Test', 'ORPH1', 'orph1', 'AMOUNT', 10000, now() - interval '1 hour',
                            now() + interval '1 day', false, 'ALL', 'ALL', 'ACTIVE', now(), now())
                    """, promoId);
            isolated.update("""
                    INSERT INTO commerce.promotion_redemption
                        (id, promotion_id, user_id, order_id, order_code, discount_vnd, state, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'DH-ORPH2', 10000, 'RESERVED', now(), now())
                    """, redemptionId, promoId, UUID.randomUUID(), UUID.randomUUID());

            assertThatThrownBy(() -> flyway(url, ORDER_MIGRATION_VERSION).migrate())
                    .isInstanceOf(FlywayException.class);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.promotion_redemption WHERE id = ?", Long.class, redemptionId))
                    .isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + orphanRedemptionDb + " WITH (FORCE)");
        }
    }

    private static void insertOrder(JdbcTemplate target, UUID id, String code, UUID userId, UUID examId,
            String productCode, String state) {
        target.update("""
                INSERT INTO commerce.purchase_order
                    (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                     product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd, attempts_granted,
                     result_view, payment_method, state, created_at, updated_at, version)
                VALUES (?, ?, ?, 'u', 'User', 'EXAM', ?, 1, ?, 'Exam', 100000, 'NONE', 100000, 1,
                        'SCORE', 'VNPAY', ?, now(), now(), 0)
                """, id, code, userId, examId, productCode, state);
    }

    private static void insertPayment(JdbcTemplate target, UUID id, UUID orderId, int seq, String txnRef,
            String state, boolean isDuplicate, String vnpTxnNo) {
        target.update("""
                INSERT INTO commerce.payment
                    (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd,
                     state, is_duplicate, vnp_transaction_no, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, '20261121100000', '20261121101500', 100000, ?, ?, ?, now(), now(), 0)
                """, id, orderId, seq, txnRef, state, isDuplicate, vnpTxnNo);
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
