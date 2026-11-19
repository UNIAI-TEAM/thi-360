package vn.unicom.thi360.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.unicom.thi360.AbstractIntegrationTest;

class Wave3SchemaMigrationIT extends AbstractIntegrationTest {

    private static final String LOCALE_MIGRATION_DESCRIPTION = "add user preferred locale";

    private static final List<String> SCHEMAS =
            List.of("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam",
                    "commerce", "attempt");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCommerceAndAttemptSchemas() {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata"
                        + " WHERE schema_name IN ('commerce', 'attempt') ORDER BY schema_name",
                String.class);

        assertThat(schemas).containsExactly("attempt", "commerce");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.flyway_schema_history"
                        + " WHERE script LIKE '%create_commerce_schema.sql'"
                        + " OR script LIKE '%create_attempt_schema.sql'",
                Long.class)).isEqualTo(2L);
    }

    @Test
    void existingAccountsGetDefaultLocale() {
        String dbName = "wave3_locale_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.execute("CREATE DATABASE " + dbName);
        try {
            String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + dbName);
            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());

            // Stop on the migration right before the locale column, insert an account, run the rest.
            flyway(url, versionBefore(url)).migrate();
            JdbcTemplate isolated = new JdbcTemplate(dataSource);
            UUID userId = UUID.randomUUID();
            isolated.update(
                    """
                    INSERT INTO identity.user_account
                        (id, code, username, username_normalized, full_name, email, email_normalized,
                         source, status, must_change_password, created_at, created_by, updated_at, updated_by,
                         version)
                    VALUES (?, 'U900001', 'Old.User', 'old.user', 'Old User', 'old@test.local', 'old@test.local',
                            1, 2, true, now(), ?, now(), ?, 3)
                    """,
                    userId, userId, userId);
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM information_schema.columns"
                            + " WHERE table_schema = 'identity' AND table_name = 'user_account'"
                            + " AND column_name = 'preferred_locale'",
                    Long.class)).isZero();
            String before = isolated.queryForObject(
                    "SELECT to_jsonb(u.*)::text FROM identity.user_account u WHERE id = ?",
                    String.class, userId);

            flyway(url, null).migrate();

            Map<String, Object> row = isolated.queryForMap(
                    "SELECT preferred_locale, (to_jsonb(u.*) - 'preferred_locale')::text AS rest"
                            + " FROM identity.user_account u WHERE id = ?",
                    userId);
            assertThat(row.get("preferred_locale")).isEqualTo("vi");
            // Every other column is exactly what it was before the migration.
            assertThat(row.get("rest")).isEqualTo(before);
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }

    /** Highest applicable version that sorts below the preferred-locale migration. */
    private static String versionBefore(String url) {
        MigrationInfo[] all = flyway(url, null).info().all();
        MigrationVersion locale = Arrays.stream(all)
                .filter(m -> LOCALE_MIGRATION_DESCRIPTION.equals(m.getDescription()))
                .map(MigrationInfo::getVersion)
                .findFirst()
                .orElseThrow();
        return Arrays.stream(all)
                .map(MigrationInfo::getVersion)
                .filter(v -> v.compareTo(locale) < 0)
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
}
