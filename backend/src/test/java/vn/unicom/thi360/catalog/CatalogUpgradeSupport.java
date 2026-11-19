package vn.unicom.thi360.catalog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/**
 * Upgrade-style ITs need rows that an older database already holds. Since UNI-2759 the versioned
 * migration {@value #SYSTEM_VALUES_MIGRATION} inserts the system values itself, so such rows must go
 * in before it runs, exactly as on a real upgrade.
 */
final class CatalogUpgradeSupport {

    static final String SYSTEM_VALUES_MIGRATION = "202611191730";

    private CatalogUpgradeSupport() {
    }

    /** Runs every versioned migration that sorts before the system-values migration, and stops there. */
    static void migrateUpToBeforeSystemValues(DataSource dataSource, Map<String, String> placeholders) {
        var base = Flyway.configure().dataSource(dataSource).defaultSchema("shared")
                .schemas("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam")
                .placeholders(placeholders)
                .locations("classpath:db/migration");
        MigrationVersion ours = MigrationVersion.fromVersion(SYSTEM_VALUES_MIGRATION);
        MigrationVersion before = Arrays.stream(base.load().info().all())
                .map(info -> info.getVersion())
                .filter(Objects::nonNull)
                .filter(version -> version.compareTo(ours) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        base.target(before).load().migrate();
    }
}
