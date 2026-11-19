package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * Nâng cấp dữ liệu đợt 2 lên schema đặt lại mật khẩu và ảnh đại diện (plan ID-2, ca rủi ro): dựng DB riêng, chạy
 * Flyway tới ngay trước migration của lane, chèn một tài khoản đợt 2, rồi chạy tới bản mới nhất. Hồ sơ vẫn đọc được
 * nguyên vẹn và chưa có ảnh.
 */
class ProfileMigrationUpgradeIT extends AbstractIntegrationTest {

    private static final String LANE_MIGRATION_DESCRIPTION = "password reset and avatar";
    private static final List<String> SCHEMAS = List.of(
            "shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam", "commerce", "attempt");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void wave2AccountSurvivesUpgradeWithEmptyAvatar() {
        String dbName = "profile_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.execute("CREATE DATABASE " + dbName);
        try {
            String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + dbName);
            JdbcTemplate isolated = new JdbcTemplate(
                    new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));

            flyway(url, versionBeforeLaneMigration(url)).migrate();
            assertThat(isolated.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'identity'"
                            + " AND table_name IN ('user_avatar', 'password_reset_token', 'avatar_object_cleanup')",
                    Long.class)).as("lane tables do not exist before the upgrade").isZero();
            UUID userId = UUID.randomUUID();
            isolated.update(
                    """
                    INSERT INTO identity.user_account
                        (id, code, username, username_normalized, full_name, email, email_normalized,
                         source, status, must_change_password, created_at, created_by, updated_at, updated_by,
                         version)
                    VALUES (?, 'U900002', 'Wave2.User', 'wave2.user', 'Wave Two', 'wave2@test.local', 'wave2@test.local',
                            1, 2, false, now(), ?, now(), ?, 3)
                    """,
                    userId, userId, userId);
            String before = isolated.queryForObject(
                    "SELECT to_jsonb(u.*)::text FROM identity.user_account u WHERE id = ?", String.class, userId);

            flyway(url, null).migrate();

            // Hồ sơ đọc như ProfileService: dòng tài khoản nguyên vẹn, nối user_avatar không có dòng nào.
            Map<String, Object> profile = isolated.queryForMap(
                    "SELECT u.username, u.full_name, u.email, u.phone, a.asset_id,"
                            + " to_jsonb(u.*)::text AS whole"
                            + " FROM identity.user_account u LEFT JOIN identity.user_avatar a ON a.user_id = u.id"
                            + " WHERE u.id = ?",
                    userId);
            assertThat(profile.get("username")).isEqualTo("Wave2.User");
            assertThat(profile.get("full_name")).isEqualTo("Wave Two");
            assertThat(profile.get("email")).isEqualTo("wave2@test.local");
            assertThat(profile.get("phone")).isNull();
            assertThat(profile.get("asset_id")).as("no avatar for a wave-2 account").isNull();
            assertThat(profile.get("whole")).as("migration leaves the account row untouched").isEqualTo(before);
            assertThat(isolated.queryForObject("SELECT count(*) FROM identity.user_avatar", Long.class)).isZero();
            assertThat(isolated.queryForObject("SELECT count(*) FROM identity.password_reset_token", Long.class))
                    .isZero();
            assertThat(isolated.queryForObject("SELECT count(*) FROM identity.avatar_object_cleanup", Long.class))
                    .isZero();
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }

    /** Phiên bản cao nhất nhỏ hơn migration của lane. */
    private static String versionBeforeLaneMigration(String url) {
        MigrationInfo[] all = flyway(url, null).info().all();
        MigrationVersion lane = Arrays.stream(all)
                .filter(m -> LANE_MIGRATION_DESCRIPTION.equals(m.getDescription()))
                .map(MigrationInfo::getVersion)
                .findFirst()
                .orElseThrow();
        return Arrays.stream(all)
                .map(MigrationInfo::getVersion)
                .filter(v -> v.compareTo(lane) < 0)
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
