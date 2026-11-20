package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.ResultAttempt;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.ActiveAttemptChecker;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Integration tests for attempt SPI implementations backed by PostgreSQL and integrated into
 * commerce & identity modules (AC-11, AC-32).
 */
class AttemptSpiIT extends AbstractAttemptDsIT {

    @Autowired private AttemptStatusProvider attemptStatusProvider;
    @Autowired private ActiveAttemptChecker activeAttemptChecker;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private StorefrontProjectionService storefrontProjectionService;

    @Test
    void statusProviderIncludesStarting() throws Exception {
        UUID userId = createStudent("spi-status");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());
        storefrontProjectionService.project(line.examId());
        try {
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();

        // 1. STARTING attempt returned by AttemptStatusProvider & HTTP endpoints
        UUID startingId = Ids.newId();
        entitlementPort.reserve(userId, line.entitlementId(), startingId);
        attemptRepository.saveAndFlush(
                Attempt.start(
                        startingId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        1L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "spi-start-seed",
                        mutableClock.instant()));

        Optional<OpenAttempt> startingOpen =
                attemptStatusProvider.findOpenAttempt(userId, line.entitlementId());
        assertThat(startingOpen).contains(new OpenAttempt(startingId, OpenAttemptState.STARTING));

        MvcResult queryRes =
                mockMvc.perform(
                                post("/api/v1/me/exam-access/query")
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codes\":[\"DS-HSA-PART-MATH-MINI\"]}"))
                        .andReturn();
        assertThat(queryRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode accessItem = json(queryRes).path("items").get(0);
        assertThat(accessItem.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(accessItem.path("activeAttemptId").asText()).isEqualTo(startingId.toString());

        MvcResult myExamsRes =
                mockMvc.perform(
                                get("/api/v1/me/exams")
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andReturn();
        assertThat(myExamsRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode card = json(myExamsRes).path("items").get(0);
        assertThat(card.path("activeAttemptId").asText()).isEqualTo(startingId.toString());
        assertThat(card.path("activeAttemptState").asText()).isEqualTo("STARTING");

        // 2. Abort STARTING, start real IN_PROGRESS attempt
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'ABORTED', abort_reason = 'GENERATION_FAILED' WHERE id = ?",
                startingId);
        entitlementPort.release(startingId);

        MvcResult realStart = performStart(userId, "spi-real-key", line.entitlementId(), null);
        assertThat(realStart.getResponse().getStatus()).isEqualTo(202);
        UUID inProgId = UUID.fromString(json(realStart).path("attemptId").asText());
        awaitTerminalStartState(inProgId, AttemptState.IN_PROGRESS);

        assertThat(attemptStatusProvider.findOpenAttempt(userId, line.entitlementId()))
                .contains(new OpenAttempt(inProgId, OpenAttemptState.IN_PROGRESS));

        // 3. Submit via real POST /me/attempts/{id}/submit -> GRADING, activeAttempt false, findOpenAttempt empty, findLatestResultAttempt present
        MvcResult submitRes = performSubmit(userId, inProgId, "spi-real-submit", null, null);
        assertThat(submitRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(submitRes).path("state").asText()).isEqualTo("GRADING");
        Attempt submitted = attemptRepository.findById(inProgId).orElseThrow();
        assertThat(submitted.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(activeAttemptChecker.hasActiveAttempt(userId)).isFalse();
        assertThat(callDeactivationCheck(UUID.randomUUID(), userId)).isFalse();
        assertThat(attemptStatusProvider.findOpenAttempt(userId, line.entitlementId())).isEmpty();
        Optional<ResultAttempt> submittedResult =
                attemptStatusProvider.findLatestResultAttempt(userId, line.entitlementId());
        assertThat(submittedResult).contains(new ResultAttempt(inProgId, submitted.getFinishedAt()));

        // 4. Mark SCORED -> findLatestResultAttempt returns scoredId, findOpenAttempt empty
        Instant finished = Instant.parse("2026-11-20T12:00:00Z");
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', finished_at = ? WHERE id = ?",
                java.sql.Timestamp.from(finished),
                inProgId);
        assertThat(attemptStatusProvider.findOpenAttempt(userId, line.entitlementId())).isEmpty();
        Optional<ResultAttempt> result =
                attemptStatusProvider.findLatestResultAttempt(userId, line.entitlementId());
        assertThat(result).contains(new ResultAttempt(inProgId, finished));
        } finally {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", line.examId());
        }
    }

    @Test
    void activeAttemptCheckerAndDeactivationCheck() throws Exception {
        UUID userId = createStudent("spi-deact");
        UUID adminId = UUID.randomUUID();

        // No attempts yet -> false
        assertThat(activeAttemptChecker.hasActiveAttempt(userId)).isFalse();
        assertThat(callDeactivationCheck(adminId, userId)).isFalse();

        // Real STARTING -> IN_PROGRESS -> SCORED transitions
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();
        UUID startingId = Ids.newId();
        attemptRepository.saveAndFlush(
                Attempt.start(
                        startingId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        2L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "deact-seed-key",
                        mutableClock.instant()));

        assertThat(activeAttemptChecker.hasActiveAttempt(userId)).isTrue();
        assertThat(callDeactivationCheck(adminId, userId)).isTrue();

        // ABORTED -> false
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'ABORTED', abort_reason = ? WHERE id = ?",
                AbortReason.STALE_STARTING.name(),
                startingId);
        assertThat(activeAttemptChecker.hasActiveAttempt(userId)).isFalse();
        assertThat(callDeactivationCheck(adminId, userId)).isFalse();

        // New IN_PROGRESS attempt -> true
        MvcResult startRes = performStart(userId, "deact-real", line.entitlementId(), null);
        UUID inProgId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(inProgId, AttemptState.IN_PROGRESS);
        assertThat(callDeactivationCheck(adminId, userId)).isTrue();

        // Closed -> false
        jdbc.update("UPDATE attempt.attempt SET state = 'GRADING' WHERE id = ?", inProgId);
        assertThat(callDeactivationCheck(adminId, userId)).isFalse();
    }

    @Test
    void learningProviderAttempts() throws Exception {
        UUID emptyUser = createStudent("spi-learn-empty");
        UUID activeUser = createStudent("spi-learn-full");
        UUID adminId = UUID.randomUUID();

        // User with 0 entitlements & 0 attempts keeps dataAvailable=false
        MvcResult emptyRes =
                mockMvc.perform(
                                get("/api/v1/admin/users/{id}/learning", emptyUser)
                                        .with(asUser(adminId, "admin", Permissions.USER_LEARNING_VIEW)))
                        .andReturn();
        assertThat(emptyRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode emptyJson = json(emptyRes);
        assertThat(emptyJson.path("dataAvailable").asBoolean()).isFalse();
        assertThat(emptyJson.path("attempts")).isEmpty();

        // User with real attempt has dataAvailable=true & populated attempts[]
        GrantOrderResult.Line line = grant(activeUser, "DS-HSA-PART-MATH-MINI", 2, List.of());
        MvcResult startRes = performStart(activeUser, "learn-real", line.entitlementId(), null);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        MvcResult fullRes =
                mockMvc.perform(
                                get("/api/v1/admin/users/{id}/learning", activeUser)
                                        .with(asUser(adminId, "admin", Permissions.USER_LEARNING_VIEW)))
                        .andReturn();
        assertThat(fullRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode fullJson = json(fullRes);
        assertThat(fullJson.path("dataAvailable").asBoolean()).isTrue();
        assertThat(fullJson.path("attempts")).hasSize(1);
        assertThat(fullJson.path("attempts").get(0).path("attemptId").asText())
                .isEqualTo(attemptId.toString());
    }

    private boolean callDeactivationCheck(UUID adminId, UUID targetUserId) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                get("/api/v1/admin/users/{id}/deactivation-check", targetUserId)
                                        .with(asUser(adminId, "admin", Permissions.USER_MODIFY)))
                        .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return json(res).path("activeAttempt").asBoolean();
    }
}
