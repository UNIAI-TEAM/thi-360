package vn.unicom.thi360.commerce.bundle;

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
 * AC-1: kiểm chứng migration {@code V202611210600__create_bundle_tables.sql}:
 * bảng bộ đề, chỉ mục/ràng buộc, mở rộng {@code storefront_product}, và ràng buộc khóa ngoại {@code bundle_id}.
 */
class BundleSchemaMigrationIT extends AbstractIntegrationTest {

    private static final String BUNDLE_MIGRATION_VERSION = "202611210600";
    private static final List<String> SCHEMAS = List.of(
            "shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam", "commerce", "attempt");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsBundleTablesConstraintsAndStorefrontColumns() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'commerce'
                  AND table_name IN ('bundle', 'bundle_item', 'bundle_version')
                ORDER BY table_name
                """, String.class);
        assertThat(tables).containsExactly("bundle", "bundle_item", "bundle_version");

        List<String> fks = jdbc.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE conname IN ('fk_purchase_order_bundle', 'fk_promotion_scope_item_bundle', 'uq_storefront_product_bundle')
                ORDER BY conname
                """, String.class);
        assertThat(fks).containsExactly(
                "fk_promotion_scope_item_bundle",
                "fk_purchase_order_bundle",
                "uq_storefront_product_bundle");

        // storefront_product chấp nhận scope = 'BUNDLE' và ràng buộc duy nhất bundle_id
        UUID sf1 = UUID.randomUUID();
        UUID sf2 = UUID.randomUUID();
        UUID orphanBundleId = UUID.randomUUID();
        String code = "MIG-B-" + shortToken();
        try {
            jdbc.update("""
                    INSERT INTO commerce.storefront_product
                        (id, product_type, bundle_id, code, code_normalized, name, exam_item_id, kind_code, scope,
                         build_mode, version_no, list_price_vnd, attempts_granted, sale_state,
                         card, detail, search_text, projection_hash, published_at, updated_at)
                    VALUES (?, 'BUNDLE', ?, ?, ?, 'Bundle Mig', ?, 'HSA', 'BUNDLE',
                            'STANDARD', 1, 100000, 2, 'ON_SALE',
                            '{}'::jsonb, '{}'::jsonb, 'bundle mig', 'hash', now(), now())
                    """, sf1, orphanBundleId, code, code.toLowerCase(), UUID.randomUUID());

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO commerce.storefront_product
                        (id, product_type, bundle_id, code, code_normalized, name, exam_item_id, kind_code, scope,
                         build_mode, version_no, list_price_vnd, attempts_granted, sale_state,
                         card, detail, search_text, projection_hash, published_at, updated_at)
                    VALUES (?, 'BUNDLE', ?, ?, ?, 'Bundle Mig 2', ?, 'HSA', 'BUNDLE',
                            'STANDARD', 1, 100000, 2, 'ON_SALE',
                            '{}'::jsonb, '{}'::jsonb, 'bundle mig 2', 'hash2', now(), now())
                    """, sf2, orphanBundleId, code + "2", (code + "2").toLowerCase(), UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE id IN (?, ?)", sf1, sf2);
        }
    }

    @Test
    void enforcesBundleForeignKeysOnExistingRowsWithoutDeletingData() {
        // Nhánh (a): bundle_id IS NULL trên purchase_order và promotion_scope_item đều migration sạch sẽ.
        String nullDb = "bundle_mig_null_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + nullDb);
        try {
            String url = jdbcUrlFor(nullDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, BUNDLE_MIGRATION_VERSION)).migrate();
            JdbcTemplate isolated = new JdbcTemplate(ds);

            UUID orderId = UUID.randomUUID();
            insertOrder(isolated, orderId, UUID.randomUUID(), null);
            UUID promoId = UUID.randomUUID();
            UUID scopeItemId = UUID.randomUUID();
            insertExamKindScopeItem(isolated, promoId, scopeItemId, UUID.randomUUID());

            flyway(url, BUNDLE_MIGRATION_VERSION).migrate();

            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.purchase_order WHERE id = ? AND bundle_id IS NULL",
                    Long.class, orderId)).isEqualTo(1L);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.promotion_scope_item WHERE id = ? AND bundle_id IS NULL",
                    Long.class, scopeItemId)).isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + nullDb + " WITH (FORCE)");
        }

        // Nhánh (b1): purchase_order có non-null bundle_id mồ côi -> Flyway thất bại và giữ nguyên dữ liệu.
        String orphanOrderDb = "bundle_mig_po_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + orphanOrderDb);
        try {
            String url = jdbcUrlFor(orphanOrderDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, BUNDLE_MIGRATION_VERSION)).migrate();
            JdbcTemplate isolated = new JdbcTemplate(ds);

            UUID orphanOrderId = UUID.randomUUID();
            insertOrder(isolated, orphanOrderId, null, UUID.randomUUID());

            assertThatThrownBy(() -> flyway(url, BUNDLE_MIGRATION_VERSION).migrate())
                    .isInstanceOf(FlywayException.class);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.purchase_order WHERE id = ?", Long.class, orphanOrderId))
                    .isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + orphanOrderDb + " WITH (FORCE)");
        }

        // Nhánh (b2): promotion_scope_item có non-null bundle_id mồ côi -> Flyway thất bại và giữ nguyên dữ liệu.
        String orphanScopeDb = "bundle_mig_ps_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + orphanScopeDb);
        try {
            String url = jdbcUrlFor(orphanScopeDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, BUNDLE_MIGRATION_VERSION)).migrate();
            JdbcTemplate isolated = new JdbcTemplate(ds);

            UUID promoId = UUID.randomUUID();
            UUID scopeItemId = UUID.randomUUID();
            insertBundleScopeItem(isolated, promoId, scopeItemId, UUID.randomUUID());

            assertThatThrownBy(() -> flyway(url, BUNDLE_MIGRATION_VERSION).migrate())
                    .isInstanceOf(FlywayException.class);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM commerce.promotion_scope_item WHERE id = ?", Long.class, scopeItemId))
                    .isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + orphanScopeDb + " WITH (FORCE)");
        }
    }

    private static void insertOrder(JdbcTemplate target, UUID orderId, UUID examId, UUID bundleId) {
        String productType = bundleId != null ? "BUNDLE" : "EXAM";
        target.update("""
                INSERT INTO commerce.purchase_order
                    (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, bundle_id,
                     exam_version_no, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                     attempts_granted, result_view, payment_method, state, created_at, updated_at, version)
                VALUES (?, ?, ?, 'u', 'User', ?, ?, ?, 1, ?, 'Product', 100000, 'NONE', 100000,
                        1, 'SCORE', 'VNPAY', 'PENDING_PAYMENT', now(), now(), 0)
                """, orderId, "DHM-" + shortToken(), UUID.randomUUID(), productType, examId, bundleId, "PRD-" + shortToken());
    }

    private static void insertExamKindScopeItem(JdbcTemplate target, UUID promoId, UUID itemId, UUID examKindId) {
        insertPromotion(target, promoId, "EXAM_KIND");
        target.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, exam_item_id)
                VALUES (?, ?, ?)
                """, itemId, promoId, examKindId);
    }

    private static void insertBundleScopeItem(JdbcTemplate target, UUID promoId, UUID itemId, UUID bundleId) {
        insertPromotion(target, promoId, "BUNDLE");
        target.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, bundle_id)
                VALUES (?, ?, ?)
                """, itemId, promoId, bundleId);
    }

    private static void insertPromotion(JdbcTemplate target, UUID promoId, String scope) {
        String code = "MIG" + shortToken();
        target.update("""
                INSERT INTO commerce.promotion
                    (id, kind, name, code, code_normalized, discount_type, discount_value, starts_at, ends_at,
                     combinable, scope, audience, status, created_at, updated_at)
                VALUES (?, 'VOUCHER', 'Mig Test', ?, ?, 'AMOUNT', 10000, now() - interval '1 hour',
                        now() + interval '1 day', false, ?, 'ALL', 'ACTIVE', now(), now())
                """, promoId, code, code.toLowerCase(), scope);
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
