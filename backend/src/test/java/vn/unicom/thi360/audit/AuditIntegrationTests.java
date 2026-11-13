package vn.unicom.thi360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.application.AuditArchiveJob;
import vn.unicom.thi360.audit.application.AuditPartitionJob;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager.QuarterPartition;
import vn.unicom.thi360.audit.infrastructure.AuditProperties;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxEventRepository;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@ExtendWith(OutputCaptureExtension.class)
class AuditIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private AuditQuery auditQuery;

    @Autowired
    private AuditPartitionManager partitionManager;

    @Autowired
    private AuditPartitionJob auditPartitionJob;

    @Autowired
    private AuditArchiveJob auditArchiveJob;

    @Autowired
    private AuditProperties auditProperties;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanTables() {
        SecurityContextHolder.clearContext();
        MDC.clear();
        outboxEventRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL thi360.audit_maintenance = 'on'");
            jdbcTemplate.execute("DELETE FROM audit.audit_event");
            jdbcTemplate.execute("DELETE FROM audit.audit_event_archive");
        });
    }

    @Test
    void migration_createsCurrentAndNextQuarterPartitionsAndArchiveYearPartition() {
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneOffset.UTC);
        int year = utcNow.getYear();
        int quarter = (utcNow.getMonthValue() - 1) / 3 + 1;
        int nextYear = quarter == 4 ? year + 1 : year;
        int nextQuarter = quarter == 4 ? 1 : quarter + 1;

        assertThat(partitionManager.isQuarterPartitionAttached(year, quarter)).isTrue();
        assertThat(partitionManager.isQuarterPartitionAttached(nextYear, nextQuarter)).isTrue();
        assertThat(partitionManager.isArchiveYearPartitionAttached(year)).isTrue();
    }

    @Test
    void rolledBackUseCase_emitsNoOutboxOrAuditEvent() {
        String targetId = "rollback-" + Ids.newId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            auditRecorder.record(AuditEvent.builder("identity", "USER_CREATED")
                    .target("USER", targetId, "rollback.user")
                    .after(Map.of("username", "rollback.user"))
                    .build());
            throw new IllegalStateException("Simulated business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.findAll()).isEmpty();
        outboxRelay.relayBatch();
        var result = auditQuery.findByTarget("USER", targetId, PageQuery.of(0, 20, null, List.of("occurredAt")));
        assertThat(result.items()).isEmpty();
    }

    @Test
    void directUpdateAndDelete_areBlockedByAppendOnlyTriggers_unlessMaintenanceBypassEnabled() {
        UUID eventId = Ids.newId();
        Instant occurredAt = Instant.now();
        insertAuditRowDirectly(eventId, occurredAt, "identity", "USER_CREATED", "USER", "u-1", "user1");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit.audit_event SET reason = 'tamper' WHERE id = ?",
                eventId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit.audit_event WHERE id = ?",
                eventId))
                .isInstanceOf(DataAccessException.class);

        UUID archiveId = Ids.newId();
        partitionManager.ensureArchiveYearPartition(2023);
        Instant oldTime = Instant.parse("2023-05-10T10:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO audit.audit_event_archive (
                    id, occurred_at, actor_type, module, action, target_type, target_id, outcome
                ) VALUES (?, ?, 3, 'audit', 'TEST', 'USER', 'u-old', 1)
                """,
                archiveId,
                Timestamp.from(oldTime));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit.audit_event_archive SET reason = 'tamper' WHERE id = ?",
                archiveId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit.audit_event_archive WHERE id = ?",
                archiveId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void sensitiveKeysAreMaskedBeforeOutboxAndInAuditStore_whileEmailAndPhoneRemain() {
        String targetId = "mask-" + Ids.newId();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("email", "old@thi360.vn");
        before.put("password", "OldPass@123");
        before.put("otpCode", "111222");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("email", "new@thi360.vn");
        after.put("phone", "0909000111");
        after.put("newPassword", "NewPass@456");
        after.put("clientSecret", "super-secret-value");
        after.put("refreshToken", "tok-secret-value");

        transactionTemplate.executeWithoutResult(status -> auditRecorder.record(
                AuditEvent.builder("identity", "USER_UPDATED")
                        .target("USER", targetId, "masked.user")
                        .before(before)
                        .after(after)
                        .build()));

        var outboxRows = outboxEventRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        String outboxPayload = outboxRows.getFirst().getPayload();
        assertThat(outboxPayload)
                .doesNotContain("OldPass@123", "NewPass@456", "super-secret-value", "tok-secret-value", "111222")
                .doesNotContain("otpCode", "newPassword", "clientSecret", "refreshToken")
                .contains("new@thi360.vn", "0909000111");

        outboxRelay.relayBatch();

        var page = auditQuery.findByTarget("USER", targetId, PageQuery.of(0, 20, null, List.of("occurredAt")));
        assertThat(page.items()).hasSize(1);
        var view = page.items().getFirst();
        assertThat(view.before())
                .containsEntry("email", "old@thi360.vn")
                .doesNotContainKeys("password", "otpCode");
        assertThat(view.after())
                .containsEntry("email", "new@thi360.vn")
                .containsEntry("phone", "0909000111")
                .doesNotContainKeys("newPassword", "clientSecret", "refreshToken");
    }

    @Test
    void resetLinkAndResetUrlAreMaskedBeforeOutboxAndInStoredAuditRow() {
        String targetId = "mask-reset-" + Ids.newId();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("resetLink", "https://thi360.vn/reset?secret=nested-1");
        nested.put("reset_url", "https://thi360.vn/reset?secret=nested-2");
        nested.put("note", "password-reset-triggered");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("email", "reset@thi360.vn");
        after.put("resetLink", "https://thi360.vn/reset?secret=top-1");
        after.put("resetUrl", "https://thi360.vn/reset?secret=top-2");
        after.put("reset-link", "https://thi360.vn/reset?secret=top-3");
        after.put("nested", nested);

        transactionTemplate.executeWithoutResult(status -> auditRecorder.record(
                AuditEvent.builder("identity", "USER_PASSWORD_RESET")
                        .target("USER", targetId, "reset.user")
                        .after(after)
                        .build()));

        var outboxRows = outboxEventRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        String outboxPayload = outboxRows.getFirst().getPayload();
        assertThat(outboxPayload)
                .doesNotContain("top-1", "top-2", "top-3", "nested-1", "nested-2")
                .doesNotContain("resetLink", "resetUrl", "reset-link", "reset_url")
                .contains("reset@thi360.vn", "password-reset-triggered");

        outboxRelay.relayBatch();

        String storedAfterJson = jdbcTemplate.queryForObject(
                "SELECT after::text FROM audit.audit_event WHERE target_id = ?",
                String.class,
                targetId);
        assertThat(storedAfterJson)
                .isNotNull()
                .doesNotContain("top-1", "top-2", "top-3", "nested-1", "nested-2")
                .doesNotContain("resetLink", "resetUrl", "reset-link", "reset_url")
                .contains("reset@thi360.vn", "password-reset-triggered");
    }

    @Test
    void reprocessingSameOutboxEvent_isIdempotentOnId() {
        String targetId = "idemp-" + Ids.newId();
        transactionTemplate.executeWithoutResult(status -> auditRecorder.record(
                AuditEvent.builder("identity", "USER_ACTIVATED")
                        .target("USER", targetId, "idemp.user")
                        .build()));

        UUID outboxId = outboxEventRepository.findAll().getFirst().getId();
        assertThat(outboxRelay.relayBatch()).isEqualTo(1);

        // Simulate duplicate delivery of the same outbox event
        jdbcTemplate.update(
                "UPDATE shared.outbox_event SET processed_at = NULL WHERE id = ?",
                outboxId);
        assertThat(outboxRelay.relayBatch()).isEqualTo(1);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE id = ?",
                Integer.class,
                outboxId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void partitionJob_withFakeClock_createsCurrentNextQuarterAndArchiveYearPartitions(CapturedOutput output) {
        Clock fakeClock = Clock.fixed(Instant.parse("2031-11-15T12:00:00Z"), ZoneOffset.UTC);
        AuditPartitionJob job = new AuditPartitionJob(partitionManager, fakeClock);

        try {
            var result = job.run();
            assertThat(result.createdPartitions())
                    .containsExactlyInAnyOrder(
                            "audit_event_2031q4",
                            "audit_event_2032q1",
                            "audit_event_archive_2031",
                            "audit_event_archive_2032");
            assertThat(partitionManager.isQuarterPartitionAttached(2031, 4)).isTrue();
            assertThat(partitionManager.isQuarterPartitionAttached(2032, 1)).isTrue();
            assertThat(partitionManager.isArchiveYearPartitionAttached(2031)).isTrue();
            assertThat(partitionManager.isArchiveYearPartitionAttached(2032)).isTrue();

            assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get("job")).isNull();
            assertThat(MDC.get("jobId")).isNull();
            assertThat(output.getOut()).contains("thi360.job").contains("Audit partition maintenance completed");
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_2031q4");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_2032q1");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_archive_2031");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_archive_2032");
            });
        }
    }

    @Test
    void archiveJob_withFakeClock_movesRowsDropsQuarterPartitionEmitsEvent_andApiReturnsArchivedData()
            throws Exception {
        partitionManager.ensureQuarterPartition(2023, 1);
        UUID oldEventId = Ids.newId();
        Instant oldOccurredAt = Instant.parse("2023-02-15T08:30:00Z");
        insertAuditRowDirectly(oldEventId, oldOccurredAt, "identity", "LOGIN", "USER", "u-2023", "legacy.user");

        Clock fakeClock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
        AuditArchiveJob job = new AuditArchiveJob(
                partitionManager,
                auditProperties,
                auditRecorder,
                transactionManager,
                fakeClock);

        try {
            var runResult = job.run();
            assertThat(runResult.archivedPartitions()).contains("audit_event_2023q1");
            assertThat(runResult.failedPartitions()).isEmpty();
            assertThat(runResult.archivedRows()).isGreaterThanOrEqualTo(1L);

            assertThat(partitionManager.isQuarterPartitionAttached(2023, 1)).isFalse();
            assertThat(partitionManager.isArchiveYearPartitionAttached(2023)).isTrue();

            // AUDIT_ARCHIVED recorded via outbox
            assertThat(outboxRelay.relayBatch()).isGreaterThanOrEqualTo(1);
            var archivedEvents = auditQuery.findByTarget(
                    "AUDIT_PARTITION",
                    "audit_event_2023q1",
                    PageQuery.of(0, 10, null, List.of("occurredAt")));
            assertThat(archivedEvents.items()).hasSize(1);
            assertThat(archivedEvents.items().getFirst().action()).isEqualTo("AUDIT_ARCHIVED");
            assertThat(archivedEvents.items().getFirst().actorType()).isEqualTo(AuditActorType.SYSTEM);

            // Default API query without 'from' reads active table only (2023 Q1 row is not in active table)
            mockMvc.perform(get("/api/v1/admin/audit-events")
                            .param("targetId", "u-2023"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));

            // API query with old 'from' reads archive and returns the 2023 Q1 row
            mockMvc.perform(get("/api/v1/admin/audit-events")
                            .param("targetId", "u-2023")
                            .param("from", "2023-01-01T00:00:00Z")
                            .param("to", "2023-12-31T23:59:59Z"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(oldEventId.toString()))
                    .andExpect(jsonPath("$.items[0].actorUsername").value("legacy.user"));
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("SET LOCAL thi360.audit_maintenance = 'on'");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_2023q1");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_archive_2023");
            });
        }
    }

    @Test
    void archiveJob_abortsAndLogsErrorOnCountMismatch_leavingPartitionIntact(CapturedOutput output) {
        partitionManager.ensureQuarterPartition(2022, 4);
        partitionManager.ensureArchiveYearPartition(2022);
        UUID conflictingId = Ids.newId();
        Instant occurredAt = Instant.parse("2022-11-01T12:00:00Z");

        insertAuditRowDirectly(conflictingId, occurredAt, "identity", "LOGIN", "USER", "u-conflict", "conflict.user");
        // Pre-insert same (id, occurred_at) into archive so ON CONFLICT DO NOTHING inserts 0 instead of 1
        jdbcTemplate.update(
                """
                INSERT INTO audit.audit_event_archive (
                    id, occurred_at, actor_type, module, action, target_type, target_id, outcome
                ) VALUES (?, ?, 1, 'identity', 'LOGIN', 'USER', 'u-conflict', 1)
                """,
                conflictingId,
                Timestamp.from(occurredAt));

        Clock fakeClock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
        AuditArchiveJob job = new AuditArchiveJob(
                partitionManager,
                auditProperties,
                auditRecorder,
                transactionManager,
                fakeClock);

        try {
            var runResult = job.run();
            assertThat(runResult.archivedPartitions()).doesNotContain("audit_event_2022q4");
            assertThat(runResult.failedPartitions()).contains("audit_event_2022q4");
            assertThat(partitionManager.isQuarterPartitionAttached(2022, 4)).isTrue();
            assertThat(partitionManager.countQuarterPartitionRows(QuarterPartition.of(2022, 4))).isEqualTo(1L);
            assertThat(output.getOut()).contains("Audit archive aborted for quarter partition");
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("SET LOCAL thi360.audit_maintenance = 'on'");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_2022q4");
                jdbcTemplate.execute("DROP TABLE IF EXISTS audit.audit_event_archive_2022");
            });
        }
    }

    @Test
    void outboxToApiEndToEnd_recordsViaOutboxAndReadsViaAdminEndpointWithFilters() throws Exception {
        UUID actorId = Ids.newId();
        var auth = new UsernamePasswordAuthenticationToken(
                actorId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put(TraceIdFilter.MDC_TRACE_ID, "trace-ac4-e2e");

        try {
            transactionTemplate.executeWithoutResult(status -> auditRecorder.record(
                    AuditEvent.builder("identity", "LOGIN")
                            .actor(actorId, "platform", List.of("PLATFORM_ADMIN"), AuditActorType.PLATFORM)
                            .target("USER", actorId.toString(), "platform")
                            .after(Map.of("realm", "master"))
                            .outcome(AuditOutcome.SUCCESS)
                            .build()));
        } finally {
            SecurityContextHolder.clearContext();
            MDC.clear();
        }

        assertThat(outboxRelay.relayBatch()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "LOGIN")
                        .param("actorId", actorId.toString())
                        .param("targetType", "USER")
                        .param("targetId", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.items[0].module").value("identity"))
                .andExpect(jsonPath("$.items[0].actorId").value(actorId.toString()))
                .andExpect(jsonPath("$.items[0].actorUsername").value("platform"))
                .andExpect(jsonPath("$.items[0].actorType").value("PLATFORM"))
                .andExpect(jsonPath("$.items[0].traceId").value("trace-ac4-e2e"))
                .andExpect(jsonPath("$.items[0].after.realm").value("master"));

        // Invalid time range returns 400 VALIDATION_FAILED
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("from", "2026-11-10T00:00:00Z")
                        .param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void insertAuditRowDirectly(
            UUID id,
            Instant occurredAt,
            String module,
            String action,
            String targetType,
            String targetId,
            String actorUsername) {
        jdbcTemplate.update(
                """
                INSERT INTO audit.audit_event (
                    id, occurred_at, actor_username, actor_type, module, action,
                    target_type, target_id, outcome
                ) VALUES (?, ?, ?, 1, ?, ?, ?, ?, 1)
                """,
                id,
                Timestamp.from(occurredAt),
                actorUsername,
                module,
                action,
                targetType,
                targetId);
    }
}
