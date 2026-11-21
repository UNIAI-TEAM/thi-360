package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Verifies Flyway upgrade {@code V202611210500__create_attempt_audio_play.sql} over pre-existing
 * AT-1..AT-4 data, PostgreSQL unique/check/cascade constraints, and full compatibility of legacy
 * zero-audio attempts with {@code AttemptStateService} and {@code AttemptLifecycleService} (R1-6,
 * R1-20).
 */
@Import(AttemptTestFixtures.MutableClockConfig.class)
class AttemptAudioSchemaMigrationIT extends AbstractIntegrationTest {

    private static final String AUDIO_MIGRATION_VERSION = "202611210500";
    private static final List<String> SCHEMAS =
            List.of(
                    "shared",
                    "identity",
                    "catalog",
                    "audit",
                    "classroom",
                    "questionbank",
                    "exam",
                    "commerce",
                    "attempt");

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableClock mutableClock;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptAudioPlayRepository attemptAudioPlayRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void initDs() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
    }

    @AfterEach
    void cleanup() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
    }

    @Test
    void upgradesPreExistingAttemptRowsEnforcesConstraintsCascadesAndLegacyRuntimeCompatibility()
            throws Exception {
        String dbName = "attempt_audio_mig_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + dbName);
        try {
            String url = jdbcUrlFor(dbName);
            DriverManagerDataSource ds =
                    new DriverManagerDataSource(
                            url, POSTGRES.getUsername(), POSTGRES.getPassword());
            String beforeVersion = versionBefore(url, AUDIO_MIGRATION_VERSION);
            assertThat(beforeVersion).isEqualTo("202611210410");
            flyway(url, beforeVersion).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);

            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            UUID attemptId = UUID.randomUUID();
            UUID part0Id = UUID.randomUUID();
            UUID part1Id = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            Instant startedAt = Instant.parse("2026-11-20T09:00:00Z");

            isolated.update(
                    """
                    INSERT INTO attempt.attempt
                        (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, attempt_no,
                         exam_name, exam_kind_code, scope, timing, navigation_mode, seed,
                         generation_token, state, started_at, result_view, max_score, idempotency_key)
                    VALUES (?, ?, ?, 1, ?, ?, 1, 'Legacy AT-4 Attempt', 'HSA', 'FULL', 'PER_PART',
                            'SEQUENTIAL', 12345, ?, 'IN_PROGRESS', ?, 'SCORE_AND_ANSWERS', 150.0000, 'mig-k1')
                    """,
                    attemptId,
                    userId,
                    examId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Timestamp.from(startedAt));

            isolated.update(
                    """
                    INSERT INTO attempt.attempt_part
                        (id, attempt_id, part_code, part_name, ord, duration_minutes,
                         question_count_required, max_points, state, seed, started_at, deadline_at)
                    VALUES (?, ?, 'P0', 'Part 0', 0, 75, 50, 50.0000, 'OPEN', 111, ?, ?)
                    """,
                    part0Id,
                    attemptId,
                    Timestamp.from(startedAt),
                    Timestamp.from(startedAt.plusSeconds(4500)));
            isolated.update(
                    """
                    INSERT INTO attempt.attempt_part
                        (id, attempt_id, part_code, part_name, ord, duration_minutes,
                         question_count_required, max_points, state, seed)
                    VALUES (?, ?, 'P1', 'Part 1', 1, 60, 50, 50.0000, 'PENDING', 222)
                    """,
                    part1Id,
                    attemptId);

            isolated.update(
                    """
                    INSERT INTO attempt.attempt_item
                        (id, attempt_id, part_id, section_id, block_id, ord, display_no, unit_count,
                         question_id, question_version_no, question_type, points_max)
                    VALUES (?, ?, ?, 'sec-0', 'blk-0', 0, 1, 1, ?, 1, 'SINGLE_CHOICE', 1.0000)
                    """,
                    itemId,
                    attemptId,
                    part0Id,
                    UUID.randomUUID());

            isolated.update(
                    """
                    INSERT INTO attempt.attempt_event
                        (id, attempt_id, type, part_id, at, data)
                    VALUES (?, ?, 'STARTED', ?, ?, '{"attemptNo":1}'::jsonb)
                    """,
                    eventId,
                    attemptId,
                    part0Id,
                    Timestamp.from(startedAt));

            // Upgrade forward through V202611210500 to latest
            flyway(url, null).migrate();

            // (a) Upgrade succeeds and pre-existing AT-1..AT-4 rows remain intact
            List<String> newTables =
                    isolated.queryForList(
                            """
                            SELECT table_name
                            FROM information_schema.tables
                            WHERE table_schema = 'attempt'
                              AND table_name IN ('audio_asset_duration', 'attempt_audio_play')
                            ORDER BY table_name
                            """,
                            String.class);
            assertThat(newTables).containsExactly("attempt_audio_play", "audio_asset_duration");
            assertThat(
                            isolated.queryForObject(
                                    "SELECT state FROM attempt.attempt WHERE id = ?",
                                    String.class,
                                    attemptId))
                    .isEqualTo("IN_PROGRESS");
            assertThat(
                            isolated.queryForObject(
                                    "SELECT COUNT(*) FROM attempt.attempt_part WHERE attempt_id = ?",
                                    Integer.class,
                                    attemptId))
                    .isEqualTo(2);
            assertThat(
                            isolated.queryForObject(
                                    "SELECT COUNT(*) FROM attempt.attempt_item WHERE attempt_id = ?",
                                    Integer.class,
                                    attemptId))
                    .isEqualTo(1);
            assertThat(
                            isolated.queryForObject(
                                    "SELECT COUNT(*) FROM attempt.attempt_event WHERE attempt_id = ?",
                                    Integer.class,
                                    attemptId))
                    .isEqualTo(1);

            // (b) PostgreSQL enforces unique, play_count, duration_ms, and position/anchor bounds
            UUID assetId = UUID.randomUUID();
            UUID playRowId = UUID.randomUUID();
            isolated.update(
                    """
                    INSERT INTO attempt.attempt_audio_play
                        (id, attempt_id, part_id, asset_id, duration_ms, play_count, state)
                    VALUES (?, ?, ?, ?, 30000, 0, 'IDLE')
                    """,
                    playRowId,
                    attemptId,
                    part0Id,
                    assetId);

            // Duplicate (attempt_id, asset_id) rejected
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            """
                                            INSERT INTO attempt.attempt_audio_play
                                                (id, attempt_id, part_id, asset_id, duration_ms, play_count, state)
                                            VALUES (?, ?, ?, ?, 30000, 0, 'IDLE')
                                            """,
                                            UUID.randomUUID(),
                                            attemptId,
                                            part0Id,
                                            assetId))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // play_count = 3 rejected
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            """
                                            INSERT INTO attempt.attempt_audio_play
                                                (id, attempt_id, part_id, asset_id, duration_ms, play_count, state)
                                            VALUES (?, ?, ?, ?, 30000, 3, 'IDLE')
                                            """,
                                            UUID.randomUUID(),
                                            attemptId,
                                            part0Id,
                                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // duration_ms = 0 rejected (both tables)
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            """
                                            INSERT INTO attempt.attempt_audio_play
                                                (id, attempt_id, part_id, asset_id, duration_ms, play_count, state)
                                            VALUES (?, ?, ?, ?, 0, 0, 'IDLE')
                                            """,
                                            UUID.randomUUID(),
                                            attemptId,
                                            part0Id,
                                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            "INSERT INTO attempt.audio_asset_duration (asset_id, duration_ms) VALUES (?, 0)",
                                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // position_ms > duration_ms rejected (R1-20)
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            """
                                            INSERT INTO attempt.attempt_audio_play
                                                (id, attempt_id, part_id, asset_id, duration_ms, play_count, state, position_ms)
                                            VALUES (?, ?, ?, ?, 30000, 1, 'PLAYING', 30001)
                                            """,
                                            UUID.randomUUID(),
                                            attemptId,
                                            part0Id,
                                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // anchor_position_ms > duration_ms rejected (R1-20)
            assertThatThrownBy(
                            () ->
                                    isolated.update(
                                            """
                                            INSERT INTO attempt.attempt_audio_play
                                                (id, attempt_id, part_id, asset_id, duration_ms, play_count, state, anchor_position_ms)
                                            VALUES (?, ?, ?, ?, 30000, 1, 'PLAYING', 30001)
                                            """,
                                            UUID.randomUUID(),
                                            attemptId,
                                            part0Id,
                                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // (c) ON DELETE CASCADE from attempt_part and from attempt
            isolated.update("DELETE FROM attempt.attempt_part WHERE id = ?", part0Id);
            assertThat(
                            isolated.queryForObject(
                                    "SELECT COUNT(*) FROM attempt.attempt_audio_play WHERE id = ?",
                                    Integer.class,
                                    playRowId))
                    .isZero();

            UUID playOnPart1 = UUID.randomUUID();
            isolated.update(
                    """
                    INSERT INTO attempt.attempt_audio_play
                        (id, attempt_id, part_id, asset_id, duration_ms, play_count, state)
                    VALUES (?, ?, ?, ?, 15000, 0, 'IDLE')
                    """,
                    playOnPart1,
                    attemptId,
                    part1Id,
                    UUID.randomUUID());
            isolated.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
            assertThat(
                            isolated.queryForObject(
                                    "SELECT COUNT(*) FROM attempt.attempt_audio_play WHERE id = ?",
                                    Integer.class,
                                    playOnPart1))
                    .isZero();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }

        // (d) In the main Spring context, verify a legacy attempt with zero attempt_audio_play rows
        // works cleanly with AttemptStateService and AttemptLifecycleService (finishPart + submitAttempt).
        UUID studentId =
                AttemptTestFixtures.createStudentAccount(
                        jdbc, "mig-zero-audio-" + shortToken().toLowerCase());
        createdUserIds.add(studentId);
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-SPT-INFORMATICS-MINI");
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line =
                AttemptTestFixtures.grantExam(
                        grantService,
                        studentId,
                        examId,
                        2,
                        List.of("CS"),
                        "SCORE_AND_ANSWERS",
                        serving.version().versionNo());

        MvcResult startRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts")
                                        .with(AttemptTestFixtures.asExamUser(studentId, "learner"))
                                        .header("Idempotency-Key", "mig-start-" + shortToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new StartAttemptRequest(
                                                                line.entitlementId(),
                                                                new StartAttemptRequest
                                                                        .PreChoiceInput(
                                                                        "CS", null)))))
                        .andReturn();
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId =
                UUID.fromString(
                        objectMapper
                                .readTree(startRes.getResponse().getContentAsString())
                                .path("attemptId")
                                .asText());
        awaitOpen(attemptId);
        assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId)).isEmpty();

        MvcResult stateRes =
                mockMvc.perform(
                                get("/api/v1/me/attempts/{attemptId}", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(studentId, "learner")))
                        .andReturn();
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode stateJson = objectMapper.readTree(stateRes.getResponse().getContentAsString());
        assertThat(stateJson.path("audioPolicy").isMissingNode() || stateJson.path("audioPolicy").isNull())
                .isTrue();
        assertThat(stateJson.path("audioPlays")).isEmpty();

        MvcResult sessionRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(studentId, "learner")))
                        .andReturn();
        assertThat(sessionRes.getResponse().getStatus()).isEqualTo(200);
        UUID sessionId =
                UUID.fromString(
                        objectMapper
                                .readTree(sessionRes.getResponse().getContentAsString())
                                .path("sessionId")
                                .asText());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(parts).hasSize(2);

        MvcResult finishRes =
                mockMvc.perform(
                                post(
                                                "/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                                                attemptId,
                                                parts.get(0).getId())
                                        .with(AttemptTestFixtures.asExamUser(studentId, "learner"))
                                        .header("X-Attempt-Session", sessionId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new FinishPartRequest(List.of()))))
                        .andReturn();
        assertThat(finishRes.getResponse().getStatus()).isEqualTo(200);

        MvcResult submitRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(studentId, "learner"))
                                        .header("Idempotency-Key", "mig-sub-" + shortToken())
                                        .header("X-Attempt-Session", sessionId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new SubmitAttemptRequest(List.of()))))
                        .andReturn();
        assertThat(submitRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(attemptRepository.findById(attemptId).orElseThrow().getState())
                .isEqualTo(AttemptState.GRADING);
    }

    private void awaitOpen(UUID attemptId) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Attempt a = attemptRepository.findById(attemptId).orElse(null);
            if (a != null && a.getState() == AttemptState.IN_PROGRESS) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " to become IN_PROGRESS");
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
        var config =
                Flyway.configure()
                        .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                        .defaultSchema("shared")
                        .schemas(SCHEMAS.toArray(String[]::new))
                        .table("flyway_schema_history")
                        .locations("classpath:db/migration")
                        .outOfOrder(true)
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
