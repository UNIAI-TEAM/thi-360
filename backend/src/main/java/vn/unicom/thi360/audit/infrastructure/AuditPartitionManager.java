package vn.unicom.thi360.audit.infrastructure;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditPartitionManager {

    private static final String AUDIT_SCHEMA = "audit";
    private static final String AUDIT_EVENT_TABLE = "audit_event";
    private static final String AUDIT_ARCHIVE_TABLE = "audit_event_archive";
    private static final Pattern QUARTER_PARTITION_PATTERN = Pattern.compile("^audit_event_(\\d{4})q([1-4])$");
    private static final Pattern ARCHIVE_PARTITION_PATTERN = Pattern.compile("^audit_event_archive_(\\d{4})$");

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AuditPartitionManager(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public boolean ensureQuarterPartitionFor(Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        int year = utc.getYear();
        int quarter = (utc.getMonthValue() - 1) / 3 + 1;
        return ensureQuarterPartition(year, quarter);
    }

    public boolean ensureQuarterPartition(int year, int quarter) {
        QuarterPartition spec = QuarterPartition.of(year, quarter);
        if (isPartitionAttached(AUDIT_EVENT_TABLE, spec.tableName())) {
            return false;
        }
        String ddl = "CREATE TABLE IF NOT EXISTS "
                + qualified(spec.tableName())
                + " PARTITION OF "
                + qualified(AUDIT_EVENT_TABLE)
                + " FOR VALUES FROM ('"
                + formatUtcBoundary(spec.startInclusive())
                + "') TO ('"
                + formatUtcBoundary(spec.endExclusive())
                + "')";
        jdbc.execute(ddl);
        return true;
    }

    public boolean ensureArchiveYearPartition(int year) {
        validateYear(year);
        String tableName = archivePartitionName(year);
        if (isPartitionAttached(AUDIT_ARCHIVE_TABLE, tableName)) {
            return false;
        }
        Instant start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant end = ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        String ddl = "CREATE TABLE IF NOT EXISTS "
                + qualified(tableName)
                + " PARTITION OF "
                + qualified(AUDIT_ARCHIVE_TABLE)
                + " FOR VALUES FROM ('"
                + formatUtcBoundary(start)
                + "') TO ('"
                + formatUtcBoundary(end)
                + "')";
        jdbc.execute(ddl);
        return true;
    }

    public List<QuarterPartition> listAttachedQuarterPartitions() {
        List<String> names = listChildRelations(AUDIT_EVENT_TABLE);
        List<QuarterPartition> partitions = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = QUARTER_PARTITION_PATTERN.matcher(name);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int quarter = Integer.parseInt(matcher.group(2));
                partitions.add(QuarterPartition.of(year, quarter));
            }
        }
        partitions.sort(Comparator.comparingInt(QuarterPartition::year).thenComparingInt(QuarterPartition::quarter));
        return List.copyOf(partitions);
    }

    public List<Integer> listAttachedArchiveYearPartitions() {
        List<String> names = listChildRelations(AUDIT_ARCHIVE_TABLE);
        List<Integer> years = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = ARCHIVE_PARTITION_PATTERN.matcher(name);
            if (matcher.matches()) {
                years.add(Integer.parseInt(matcher.group(1)));
            }
        }
        years.sort(Comparator.naturalOrder());
        return List.copyOf(years);
    }

    public boolean isQuarterPartitionAttached(int year, int quarter) {
        return isPartitionAttached(AUDIT_EVENT_TABLE, QuarterPartition.of(year, quarter).tableName());
    }

    public boolean isArchiveYearPartitionAttached(int year) {
        validateYear(year);
        return isPartitionAttached(AUDIT_ARCHIVE_TABLE, archivePartitionName(year));
    }

    public void enableMaintenanceBypassForTransaction() {
        jdbc.execute("SET LOCAL thi360.audit_maintenance = 'on'");
    }

    public long countQuarterPartitionRows(QuarterPartition partition) {
        QuarterPartition canonical = requireAttachedQuarterPartition(partition);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + qualified(canonical.tableName()),
                Long.class);
        return count == null ? 0L : count;
    }

    public long copyQuarterPartitionToArchive(QuarterPartition partition) {
        QuarterPartition canonical = requireAttachedQuarterPartition(partition);
        String sql = """
                INSERT INTO audit.audit_event_archive (
                    id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                    module, action, target_type, target_id, target_label,
                    before, after, reason, outcome, error_code, trace_id, ip
                )
                SELECT
                    id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                    module, action, target_type, target_id, target_label,
                    before, after, reason, outcome, error_code, trace_id, ip
                FROM %s
                ON CONFLICT (id, occurred_at) DO NOTHING
                """.formatted(qualified(canonical.tableName()));
        return jdbc.update(sql);
    }

    public long countArchiveRowsInRange(Instant startInclusive, Instant endExclusive) {
        Long count = namedJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit.audit_event_archive
                WHERE occurred_at >= :start
                  AND occurred_at < :end
                """,
                new MapSqlParameterSource()
                        .addValue("start", java.sql.Timestamp.from(startInclusive))
                        .addValue("end", java.sql.Timestamp.from(endExclusive)),
                Long.class);
        return count == null ? 0L : count;
    }

    public void detachAndDropQuarterPartition(QuarterPartition partition) {
        QuarterPartition canonical = requireAttachedQuarterPartition(partition);
        String child = qualified(canonical.tableName());
        jdbc.execute("ALTER TABLE " + qualified(AUDIT_EVENT_TABLE) + " DETACH PARTITION " + child);
        jdbc.execute("DROP TABLE " + child);
    }

    private QuarterPartition requireAttachedQuarterPartition(QuarterPartition partition) {
        Objects.requireNonNull(partition, "partition must not be null");
        QuarterPartition canonical = QuarterPartition.of(partition.year(), partition.quarter());
        if (!canonical.equals(partition)) {
            throw new IllegalArgumentException("Non-canonical quarter partition: " + partition);
        }
        if (!isPartitionAttached(AUDIT_EVENT_TABLE, canonical.tableName())) {
            throw new IllegalStateException("Quarter partition is not attached: " + canonical.tableName());
        }
        return canonical;
    }

    private List<String> listChildRelations(String parentTable) {
        return namedJdbc.queryForList(
                """
                SELECT child.relname
                FROM pg_inherits inh
                JOIN pg_class parent ON parent.oid = inh.inhparent
                JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
                JOIN pg_class child ON child.oid = inh.inhrelid
                JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
                WHERE parent_ns.nspname = :schema
                  AND child_ns.nspname = :schema
                  AND parent.relname = :parent
                """,
                new MapSqlParameterSource()
                        .addValue("schema", AUDIT_SCHEMA)
                        .addValue("parent", parentTable),
                String.class);
    }

    private boolean isPartitionAttached(String parentTable, String childTable) {
        Boolean exists = namedJdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_inherits inh
                    JOIN pg_class parent ON parent.oid = inh.inhparent
                    JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
                    JOIN pg_class child ON child.oid = inh.inhrelid
                    JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
                    WHERE parent_ns.nspname = :schema
                      AND child_ns.nspname = :schema
                      AND parent.relname = :parent
                      AND child.relname = :child
                )
                """,
                new MapSqlParameterSource()
                        .addValue("schema", AUDIT_SCHEMA)
                        .addValue("parent", parentTable)
                        .addValue("child", childTable),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static String archivePartitionName(int year) {
        return "audit_event_archive_" + year;
    }

    private static String qualified(String tableName) {
        return AUDIT_SCHEMA + ".\"" + tableName + "\"";
    }

    private static void validateYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Audit partition year out of supported range [2000, 2100]: " + year);
        }
    }

    private static String formatUtcBoundary(Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        return String.format(
                "%04d-%02d-%02d %02d:%02d:%02d+00",
                zdt.getYear(),
                zdt.getMonthValue(),
                zdt.getDayOfMonth(),
                zdt.getHour(),
                zdt.getMinute(),
                zdt.getSecond());
    }

    public record QuarterPartition(
            String tableName,
            int year,
            int quarter,
            Instant startInclusive,
            Instant endExclusive
    ) {
        public QuarterPartition {
            validateYear(year);
            if (quarter < 1 || quarter > 4) {
                throw new IllegalArgumentException("Audit quarter must be between 1 and 4: " + quarter);
            }
            int startMonth = (quarter - 1) * 3 + 1;
            ZonedDateTime startZdt = ZonedDateTime.of(year, startMonth, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            Instant expectedStart = startZdt.toInstant();
            Instant expectedEnd = startZdt.plusMonths(3).toInstant();
            String expectedName = "audit_event_" + year + "q" + quarter;
            if (!expectedName.equals(tableName)) {
                throw new IllegalArgumentException("Non-canonical quarter partition tableName: " + tableName);
            }
            if (!expectedStart.equals(startInclusive) || !expectedEnd.equals(endExclusive)) {
                throw new IllegalArgumentException("Non-canonical quarter partition bounds for " + expectedName);
            }
        }

        public static QuarterPartition of(int year, int quarter) {
            validateYear(year);
            if (quarter < 1 || quarter > 4) {
                throw new IllegalArgumentException("Audit quarter must be between 1 and 4: " + quarter);
            }
            int startMonth = (quarter - 1) * 3 + 1;
            ZonedDateTime startZdt = ZonedDateTime.of(year, startMonth, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            ZonedDateTime endZdt = startZdt.plusMonths(3);
            String name = "audit_event_" + year + "q" + quarter;
            return new QuarterPartition(name, year, quarter, startZdt.toInstant(), endZdt.toInstant());
        }

        public int totalQuarters() {
            return year * 4 + (quarter - 1);
        }
    }
}
