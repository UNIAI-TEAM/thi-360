package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for {@code POST /api/v1/me/attempts/{id}/submit} idempotency, forbidden-key
 * sanitization across both {@code finish} and {@code submit}, and ownership/role access control
 * (AC-16, AC-18, AC-19).
 */
class AttemptSubmitIdempotencyIT extends AbstractAttemptDsIT {

    @Test
    void submitIdempotencyReplayConflictMissingAndPostClose() throws Exception {
        UUID userId = createStudent("submit-idem");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "sub-idem", line.entitlementId(), null);
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        UUID itemId = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst().getId();

        // 1. Missing and blank Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        MvcResult missing = performSubmit(userId, attempt.getId(), null, null, null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missing).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        MvcResult blank = performSubmit(userId, attempt.getId(), "   ", null, null);
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(blank).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        // 2. First submit with key "sub-k1" -> 200 GRADING / SUBMITTED
        SubmitAttemptRequest req1 =
                new SubmitAttemptRequest(
                        List.of(
                                new FinalAnswerChange(
                                        itemId, 1L, new AnswerValue.SingleChoice("opt-1"))));
        MvcResult first = performSubmit(userId, attempt.getId(), "sub-k1", null, req1);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String firstBody = first.getResponse().getContentAsString();
        JsonNode firstJson = json(first);
        assertThat(firstJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(firstJson.path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);

        // 3. Replay same Idempotency-Key + same body -> exact same SubmitAttemptResponse without re-closing
        MvcResult replay = performSubmit(userId, attempt.getId(), "sub-k1", null, req1);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(firstBody);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);

        // 4. Same Idempotency-Key + different body -> 409 IDEMPOTENCY_KEY_REUSED
        SubmitAttemptRequest req2 =
                new SubmitAttemptRequest(
                        List.of(
                                new FinalAnswerChange(
                                        itemId, 2L, new AnswerValue.SingleChoice("opt-2"))));
        MvcResult conflict = performSubmit(userId, attempt.getId(), "sub-k1", null, req2);
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(conflict).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // 5. Different Idempotency-Key after attempt already closed -> 200 recorded state, no second close
        MvcResult postClose = performSubmit(userId, attempt.getId(), "sub-k2", null, req2);
        assertThat(postClose.getResponse().getStatus()).isEqualTo(200);
        JsonNode postCloseJson = json(postClose);
        assertThat(postCloseJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(postCloseJson.path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(postCloseJson.path("finishedAt").asText())
                .isEqualTo(firstJson.path("finishedAt").asText());
        assertThat(postCloseJson.path("results")).isEmpty();
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void forbiddenKeysAbsent() throws Exception {
        UUID userId = createStudent("idem-forbid");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "forbid-life", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        UUID itemId =
                attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).getFirst().getId();

        MvcResult finRes =
                performFinishPart(
                        userId,
                        attempt.getId(),
                        parts.get(0).getId(),
                        null,
                        new FinishPartRequest(
                                List.of(
                                        new FinalAnswerChange(
                                                itemId,
                                                1L,
                                                new AnswerValue.SingleChoice("opt-1")))));
        assertThat(finRes.getResponse().getStatus()).isEqualTo(200);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(finRes.getResponse().getContentAsString());

        // Also scan submit on a single-part attempt
        GrantOrderResult.Line singleLine = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt single = startAndAwaitOpen(userId, "forbid-sub", singleLine.entitlementId(), null);
        AttemptPart singlePart =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single.getId()).getFirst();
        UUID singleItemId =
                attemptItemRepository.findByPartIdOrderByOrdAsc(singlePart.getId()).getFirst().getId();

        MvcResult subRes =
                performSubmit(
                        userId,
                        single.getId(),
                        "forbid-sub-k",
                        null,
                        new SubmitAttemptRequest(
                                List.of(
                                        new FinalAnswerChange(
                                                singleItemId,
                                                1L,
                                                new AnswerValue.SingleChoice("opt-1")))));
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(subRes.getResponse().getContentAsString());
    }

    @Test
    void ownershipAndAccess() throws Exception {
        UUID owner = createStudent("life-owner");
        UUID stranger = createStudent("life-stranger");
        UUID manager = createManager("life-manager");

        GrantOrderResult.Line line = grant(owner, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(owner, "life-auth", line.entitlementId(), null);
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();

        // 1. Anonymous -> 401 on finish and submit
        assertThat(
                        mockMvc.perform(
                                        post(
                                                "/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                                                attempt.getId(),
                                                p0.getId()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
        assertThat(
                        mockMvc.perform(
                                        post("/api/v1/me/attempts/{attemptId}/submit", attempt.getId())
                                                .header("Idempotency-Key", "anon-sub"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);

        // 2. MANAGE account -> 403 ATTEMPT_ACCESS_FORBIDDEN on finish and submit
        MvcResult mgrFin =
                mockMvc.perform(
                                post(
                                                "/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                                                attempt.getId(),
                                                p0.getId())
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                        .andReturn();
        assertThat(mgrFin.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrFin).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        MvcResult mgrSub =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/submit", attempt.getId())
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr"))
                                        .header("Idempotency-Key", "mgr-sub")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(mgrSub.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrSub).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        // 3. Other learner -> 404 NOT_FOUND on finish and submit
        MvcResult strFin = performFinishPart(stranger, attempt.getId(), p0.getId(), null, null);
        assertThat(strFin.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(strFin).path("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult strSub = performSubmit(stranger, attempt.getId(), "str-sub", null, null);
        assertThat(strSub.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(strSub).path("code").asText()).isEqualTo("NOT_FOUND");
    }
}
