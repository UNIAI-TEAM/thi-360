package vn.unicom.thi360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.audit.application.AuditArchiveJob;
import vn.unicom.thi360.audit.application.AuditPartitionJob;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager;
import vn.unicom.thi360.audit.infrastructure.AuditProperties;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.ids.Ids;

class AuditSharedRoleIntegrationTests {

    @Test
    void sharedNonSuperuserCreateroleOwner_migratesAndRunsAuditWritesAndJobs() {
        String dbName = "thi360_shared_audit_db";
        String sharedRole = "thi360_shared_owner";
        String sharedPass = "thi360-shared-owner-pass";

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres-admin")) {
            postgres.start();

            JdbcTemplate adminJdbc = new JdbcTemplate(new SimpleDriverDataSource(
                    new org.postgresql.Driver(),
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()));
            adminJdbc.execute(
                    "CREATE ROLE " + sharedRole + " LOGIN PASSWORD '" + sharedPass + "' NOSUPERUSER CREATEROLE");
            adminJdbc.execute("CREATE DATABASE " + dbName + " OWNER " + sharedRole);

            String sharedDbUrl = "jdbc:postgresql://"
                    + postgres.getHost()
                    + ":"
                    + postgres.getMappedPort(5432)
                    + "/"
                    + dbName;

            Flyway.configure()
                    .dataSource(sharedDbUrl, sharedRole, sharedPass)
                    .defaultSchema("shared")
                    .schemas("shared", "identity", "catalog", "audit")
                    .table("flyway_schema_history")
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("auditAppRole", sharedRole))
                    .load()
                    .migrate();

            SimpleDriverDataSource sharedDataSource = new SimpleDriverDataSource(
                    new org.postgresql.Driver(),
                    sharedDbUrl,
                    sharedRole,
                    sharedPass);
            JdbcTemplate sharedJdbc = new JdbcTemplate(sharedDataSource);
            NamedParameterJdbcTemplate sharedNamedJdbc = new NamedParameterJdbcTemplate(sharedDataSource);
            DataSourceTransactionManager sharedTxManager = new DataSourceTransactionManager(sharedDataSource);
            TransactionTemplate sharedTxTemplate = new TransactionTemplate(sharedTxManager);
            AuditPartitionManager partitionManager = new AuditPartitionManager(sharedJdbc, sharedNamedJdbc);

            assertThat(sharedJdbc.queryForObject(
                    "SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                    Boolean.class)).isFalse();
            assertThat(sharedJdbc.queryForObject(
                    "SELECT rolcreaterole FROM pg_roles WHERE rolname = current_user",
                    Boolean.class)).isTrue();
            assertThat(sharedJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(sharedRole);
            assertThat(sharedJdbc.queryForObject(
                    "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'thi360_audit_maint'",
                    Boolean.class)).isFalse();
            assertThat(sharedJdbc.queryForObject(
                    "SELECT pg_has_role(current_user, 'thi360_audit_maint', 'MEMBER')",
                    Boolean.class)).isTrue();
            assertThat(sharedJdbc.queryForObject(
                    "SELECT pg_has_role(current_user, 'thi360_audit_maint', 'SET')",
                    Boolean.class)).isTrue();
            assertThat(sharedJdbc.queryForObject(
                    "SELECT pg_has_role(current_user, 'thi360_audit_maint', 'USAGE')",
                    Boolean.class)).isFalse();

            assertThat(ownerOf(sharedJdbc, "audit_event")).isEqualTo("thi360_audit_maint");
            assertThat(ownerOf(sharedJdbc, "audit_event_archive")).isEqualTo("thi360_audit_maint");

            // Actual INSERT + SELECT on audit.audit_event (initial current-quarter partition)
            Instant now = Instant.now();
            UUID eventId = Ids.newId();
            String targetId = "shared-cur-" + Ids.newId();
            sharedJdbc.update(
                    """
                    INSERT INTO audit.audit_event (
                        id, occurred_at, actor_username, actor_type, module, action,
                        target_type, target_id, outcome
                    ) VALUES (?, ?, 'shared.user', 1, 'identity', 'LOGIN', 'USER', ?, 1)
                    """,
                    eventId,
                    Timestamp.from(now),
                    targetId);
            assertThat(sharedJdbc.queryForObject(
                    "SELECT action FROM audit.audit_event WHERE id = ?",
                    String.class,
                    eventId)).isEqualTo("LOGIN");

            // Actual INSERT + SELECT on audit.audit_event_archive (initial current-year partition)
            UUID archiveId = Ids.newId();
            sharedJdbc.update(
                    """
                    INSERT INTO audit.audit_event_archive (
                        id, occurred_at, actor_type, module, action, target_type, target_id, outcome
                    ) VALUES (?, ?, 3, 'audit', 'AUDIT_INITIAL_CHECK', 'USER', ?, 1)
                    """,
                    archiveId,
                    Timestamp.from(now),
                    targetId);
            assertThat(sharedJdbc.queryForObject(
                    "SELECT action FROM audit.audit_event_archive WHERE id = ?",
                    String.class,
                    archiveId)).isEqualTo("AUDIT_INITIAL_CHECK");

            // UPDATE and DELETE rejected on both parent tables
            assertThatThrownBy(() -> sharedJdbc.update(
                    "UPDATE audit.audit_event SET reason = 'tamper' WHERE id = ?",
                    eventId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> sharedJdbc.update(
                    "DELETE FROM audit.audit_event WHERE id = ?",
                    eventId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> sharedJdbc.update(
                    "UPDATE audit.audit_event_archive SET reason = 'tamper' WHERE id = ?",
                    archiveId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> sharedJdbc.update(
                    "DELETE FROM audit.audit_event_archive WHERE id = ?",
                    archiveId))
                    .isInstanceOf(DataAccessException.class);

            ZonedDateTime utcNow = ZonedDateTime.now(ZoneOffset.UTC);
            int curYear = utcNow.getYear();
            int curQuarter = (utcNow.getMonthValue() - 1) / 3 + 1;
            String curPartition = "audit_event_" + curYear + "q" + curQuarter;
            assertThatThrownBy(() -> sharedJdbc.execute(
                    "ALTER TABLE audit.audit_event DETACH PARTITION audit." + curPartition))
                    .isInstanceOf(DataAccessException.class);

            // AuditPartitionJob creates next quarter (and future year) partitions with working default ACLs
            Clock partitionClock = Clock.fixed(Instant.parse("2033-11-15T12:00:00Z"), ZoneOffset.UTC);
            AuditPartitionJob partitionJob = new AuditPartitionJob(partitionManager, sharedTxManager, partitionClock);
            var partitionResult = partitionJob.run();
            assertThat(partitionResult.createdPartitions())
                    .containsExactlyInAnyOrder(
                            "audit_event_2033q4",
                            "audit_event_2034q1",
                            "audit_event_archive_2033",
                            "audit_event_archive_2034");
            assertThat(partitionManager.isQuarterPartitionAttached(2034, 1)).isTrue();
            assertThat(ownerOf(sharedJdbc, "audit_event_2034q1")).isEqualTo("thi360_audit_maint");

            UUID futureEventId = Ids.newId();
            sharedJdbc.update(
                    """
                    INSERT INTO audit.audit_event (
                        id, occurred_at, actor_username, actor_type, module, action,
                        target_type, target_id, outcome
                    ) VALUES (?, ?, 'shared.user', 1, 'identity', 'FUTURE_WRITE', 'USER', ?, 1)
                    """,
                    futureEventId,
                    Timestamp.from(Instant.parse("2034-01-15T12:00:00Z")),
                    targetId);
            assertThat(sharedJdbc.queryForObject(
                    "SELECT action FROM audit.audit_event_2034q1 WHERE id = ?",
                    String.class,
                    futureEventId)).isEqualTo("FUTURE_WRITE");

            // Seed an old quarter and verify AuditArchiveJob archives it cleanly as shared non-superuser role
            sharedTxTemplate.executeWithoutResult(status -> {
                partitionManager.assumeMaintenanceRoleForTransaction();
                partitionManager.ensureQuarterPartition(2020, 1);
                partitionManager.resetRole();
            });

            UUID oldEventId = Ids.newId();
            sharedJdbc.update(
                    """
                    INSERT INTO audit.audit_event (
                        id, occurred_at, actor_username, actor_type, module, action,
                        target_type, target_id, outcome
                    ) VALUES (?, ?, 'shared.user', 1, 'identity', 'OLD_EVENT', 'USER', ?, 1)
                    """,
                    oldEventId,
                    Timestamp.from(Instant.parse("2020-02-10T09:00:00Z")),
                    targetId);

            List<AuditEvent> recordedEvents = new ArrayList<>();
            AuditRecorder recordingAuditRecorder = event -> {
                assertThat(sharedJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(sharedRole);
                recordedEvents.add(event);
            };
            Clock archiveClock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
            AuditArchiveJob archiveJob = new AuditArchiveJob(
                    partitionManager,
                    new AuditProperties(8),
                    recordingAuditRecorder,
                    sharedTxManager,
                    archiveClock);

            var archiveResult = archiveJob.run();
            assertThat(archiveResult.archivedPartitions()).containsExactly("audit_event_2020q1");
            assertThat(archiveResult.failedPartitions()).isEmpty();
            assertThat(archiveResult.archivedRows()).isEqualTo(1L);
            assertThat(partitionManager.isQuarterPartitionAttached(2020, 1)).isFalse();
            assertThat(sharedJdbc.queryForObject(
                    "SELECT action FROM audit.audit_event_archive WHERE id = ?",
                    String.class,
                    oldEventId)).isEqualTo("OLD_EVENT");
            assertThat(recordedEvents).hasSize(1);
            assertThat(recordedEvents.getFirst().action()).isEqualTo(AuditArchiveJob.ACTION_AUDIT_ARCHIVED);
        }
    }

    private static String ownerOf(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject(
                """
                SELECT pg_get_userbyid(c.relowner)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'audit' AND c.relname = ?
                """,
                String.class,
                tableName);
    }
}
