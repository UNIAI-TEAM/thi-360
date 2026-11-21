package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Integration tests for attempt edit sessions, takeover revocation, heartbeats, and session audit
 * events (AC-12..AC-15, AC-18, AC-19).
 */
class AttemptSessionIT extends AbstractAttemptDsIT {

    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void sameSessionIdIsReclaimed() throws Exception {
        UUID userId = createStudent("sess-reclaim");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "reclaim", line.entitlementId(), null);

        UUID requestedSid = UUID.randomUUID();
        MvcResult first = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(requestedSid, false));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        JsonNode firstJson = json(first);
        UUID activeSid = UUID.fromString(firstJson.path("sessionId").asText());
        assertThat(activeSid).isEqualTo(requestedSid);
        Instant t1 = Instant.parse(firstJson.path("serverNow").asText());

        mutableClock.advance(Duration.ofSeconds(45));
        MvcResult reclaim = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(activeSid, null));
        assertThat(reclaim.getResponse().getStatus()).isEqualTo(200);
        JsonNode reclaimJson = json(reclaim);
        assertThat(UUID.fromString(reclaimJson.path("sessionId").asText())).isEqualTo(activeSid);
        Instant t2 = Instant.parse(reclaimJson.path("serverNow").asText());
        assertThat(t2).isAfter(t1);

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getEditSessionId()).isEqualTo(activeSid);
        assertThat(reloaded.getEditSessionSeenAt()).isEqualTo(t2);
    }

    @Test
    void otherSessionAlwaysAsks() throws Exception {
        UUID userId = createStudent("sess-elsewhere");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "elsewhere", line.entitlementId(), null);

        // Initial claim without client sessionId generates a fresh UUID on server
        MvcResult claimA = performClaimSession(userId, attempt.getId(), null);
        assertThat(claimA.getResponse().getStatus()).isEqualTo(200);
        UUID sidA = UUID.fromString(json(claimA).path("sessionId").asText());
        Instant seenAt = Instant.parse(json(claimA).path("serverNow").asText());

        // Different device immediately asks without takeover -> 409 ATTEMPT_OPEN_ELSEWHERE + lastSeenAt
        UUID sidB = UUID.randomUUID();
        MvcResult conflictNow = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidB, false));
        assertThat(conflictNow.getResponse().getStatus()).isEqualTo(409);
        JsonNode conflictNowJson = json(conflictNow);
        assertThat(conflictNowJson.path("code").asText()).isEqualTo("ATTEMPT_OPEN_ELSEWHERE");
        assertThat(Instant.parse(conflictNowJson.path("lastSeenAt").asText())).isEqualTo(seenAt);

        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getEditSessionId()).isEqualTo(sidA);
    }

    @Test
    void silentSessionStillAsks() throws Exception {
        UUID userId = createStudent("sess-silent");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "silent", line.entitlementId(), null);

        MvcResult claimA = performClaimSession(userId, attempt.getId(), null);
        assertThat(claimA.getResponse().getStatus()).isEqualTo(200);
        UUID sidA = UUID.fromString(json(claimA).path("sessionId").asText());
        Instant seenAt = Instant.parse(json(claimA).path("serverNow").asText());

        // Exact 2 minutes later -> STILL asks (LT F10)
        UUID sidB = UUID.randomUUID();
        mutableClock.setAt(seenAt.plus(Duration.ofMinutes(2)));
        MvcResult conflictExact2m = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidB, null));
        assertThat(conflictExact2m.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(conflictExact2m).path("code").asText()).isEqualTo("ATTEMPT_OPEN_ELSEWHERE");
        assertThat(Instant.parse(json(conflictExact2m).path("lastSeenAt").asText())).isEqualTo(seenAt);

        // Silent > 2 minutes (e.g. 10 minutes, offline old device) -> STILL asks (LT F10, AC-13)
        mutableClock.setAt(seenAt.plus(Duration.ofMinutes(10)));
        MvcResult conflictSilent = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(null, false));
        assertThat(conflictSilent.getResponse().getStatus()).isEqualTo(409);
        JsonNode conflictSilentJson = json(conflictSilent);
        assertThat(conflictSilentJson.path("code").asText()).isEqualTo("ATTEMPT_OPEN_ELSEWHERE");
        assertThat(Instant.parse(conflictSilentJson.path("lastSeenAt").asText())).isEqualTo(seenAt);

        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getEditSessionId()).isEqualTo(sidA);
    }

    @Test
    void takeoverRevokesOldSession() throws Exception {
        UUID userId = createStudent("sess-takeover");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "takeover", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        UUID sidA = UUID.randomUUID();
        assertThat(performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidA, false))
                .getResponse().getStatus()).isEqualTo(200);

        mutableClock.advance(Duration.ofSeconds(30));
        UUID sidB = UUID.randomUUID();
        MvcResult takeoverRes = performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidB, true));
        assertThat(takeoverRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(UUID.fromString(json(takeoverRes).path("sessionId").asText())).isEqualTo(sidB);

        // Old session gets 409 ATTEMPT_SESSION_REVOKED across answers, heartbeat, finish, and submit
        UUID p0ItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).getFirst().getId();
        MvcResult oldAnswers =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sidA,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p0ItemId, 1L, new AnswerValue.SingleChoice("a"))),
                                null,
                                null));
        assertThat(oldAnswers.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(oldAnswers).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptItemRepository.findById(p0ItemId).orElseThrow().getAnswer()).isNull();

        MvcResult oldHeartbeat = performHeartbeat(userId, attempt.getId(), sidA);
        assertThat(oldHeartbeat.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(oldHeartbeat).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

        MvcResult oldFinish = performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sidA, null);
        assertThat(oldFinish.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(oldFinish).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptPartRepository.findById(parts.get(0).getId()).orElseThrow().getState())
                .isEqualTo(PartState.OPEN);

        MvcResult oldSubmit = performSubmit(userId, attempt.getId(), "revoked-sub", sidA, null);
        assertThat(oldSubmit.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(oldSubmit).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);

        // Missing X-Attempt-Session header is also rejected with 409 ATTEMPT_SESSION_REVOKED
        MvcResult missingHeader = performHeartbeat(userId, attempt.getId(), null);
        assertThat(missingHeader.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(missingHeader).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

        // Active session sidB still succeeds on heartbeat, finish, and submit
        assertThat(performHeartbeat(userId, attempt.getId(), sidB).getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sidB, null)
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void heartbeatUpdatesSeenAt() throws Exception {
        UUID userId = createStudent("sess-hb");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "hb", line.entitlementId(), null);

        MvcResult claimRes = performClaimSession(userId, attempt.getId(), null);
        UUID sid = UUID.fromString(json(claimRes).path("sessionId").asText());
        Instant tClaim = Instant.parse(json(claimRes).path("serverNow").asText());

        mutableClock.advance(Duration.ofSeconds(30));
        MvcResult hbRes = performHeartbeat(userId, attempt.getId(), sid);
        assertThat(hbRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode hbJson = json(hbRes);
        Instant tHb = Instant.parse(hbJson.path("serverNow").asText());
        assertThat(tHb).isEqualTo(tClaim.plusSeconds(30));
        assertThat(hbJson.path("state").asText()).isEqualTo("IN_PROGRESS");

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getEditSessionSeenAt()).isEqualTo(tHb);

        // After submit, active session heartbeat returns terminal state without failing, while revoked gets 409
        assertThat(performSubmit(userId, attempt.getId(), "hb-sub", sid, null).getResponse().getStatus())
                .isEqualTo(200);
        mutableClock.advance(Duration.ofSeconds(10));
        MvcResult postSubmitHb = performHeartbeat(userId, attempt.getId(), sid);
        assertThat(postSubmitHb.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(postSubmitHb).path("state").asText()).isEqualTo("GRADING");
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getEditSessionSeenAt())
                .isEqualTo(tHb);
    }

    @Test
    void eventsCarryNoAnswerContent() throws Exception {
        UUID userId = createStudent("sess-events");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "sess-ev", line.entitlementId(), null);

        UUID sid1 = UUID.randomUUID();
        assertThat(performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sid1, false))
                .getResponse().getStatus()).isEqualTo(200);
        mutableClock.advance(Duration.ofSeconds(10));
        assertThat(performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sid1, false))
                .getResponse().getStatus()).isEqualTo(200);
        mutableClock.advance(Duration.ofSeconds(10));
        UUID sid2 = UUID.randomUUID();
        assertThat(performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sid2, true))
                .getResponse().getStatus()).isEqualTo(200);

        String sentinelText = "SENTINEL_SECRET_ANSWER_DO_NOT_LOG";
        UUID missingItemId = UUID.randomUUID();
        MvcResult rejectedSave =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid2,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(
                                                missingItemId,
                                                7L,
                                                new AnswerValue.ShortAnswer(sentinelText))),
                                null,
                                null));
        assertThat(rejectedSave.getResponse().getStatus()).isEqualTo(200);

        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId());
        List<AttemptEvent> claimed = events.stream()
                .filter(e -> e.getType() == AttemptEventType.SESSION_CLAIMED)
                .toList();
        assertThat(claimed).hasSize(2);
        assertThat(claimed.get(0).getSessionId()).isEqualTo(sid1);
        assertThat(claimed.get(0).getData()).containsEntry("reclaimed", false);
        assertThat(claimed.get(1).getSessionId()).isEqualTo(sid1);
        assertThat(claimed.get(1).getData()).containsEntry("reclaimed", true);

        AttemptEvent takenOver = events.stream()
                .filter(e -> e.getType() == AttemptEventType.SESSION_TAKEN_OVER)
                .findFirst()
                .orElseThrow();
        assertThat(takenOver.getSessionId()).isEqualTo(sid2);
        assertThat(takenOver.getData()).containsEntry("previousSessionId", sid1.toString());

        AttemptEvent saveRejected = events.stream()
                .filter(e -> e.getType() == AttemptEventType.SAVE_REJECTED)
                .findFirst()
                .orElseThrow();
        assertThat(saveRejected.getSessionId()).isEqualTo(sid2);
        assertThat(saveRejected.getData())
                .containsEntry("itemId", missingItemId.toString())
                .containsEntry("seq", 7)
                .containsEntry("reason", "NOT_IN_SCOPE");

        for (AttemptEvent evt : events) {
            String raw = objectMapper.writeValueAsString(evt.getData());
            assertThat(raw).doesNotContain(sentinelText)
                    .doesNotContain("\"answer\"")
                    .doesNotContain("\"optionId\"")
                    .doesNotContain("\"optionIds\"")
                    .doesNotContain("\"text\"")
                    .doesNotContain("\"html\"")
                    .doesNotContain("\"blanks\"")
                    .doesNotContain("\"statements\"")
                    .doesNotContain("\"pairs\"");
        }
    }

    @Test
    void concurrentTakeoversLeaveSingleActiveSession() throws Exception {
        UUID userId = createStudent("sess-race");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "sess-race", line.entitlementId(), null);

        UUID sidInitial = UUID.randomUUID();
        assertThat(performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidInitial, false))
                .getResponse().getStatus()).isEqualTo(200);

        UUID sidB = UUID.randomUUID();
        UUID sidC = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<MvcResult> taskB = () -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidB, true));
            };
            Callable<MvcResult> taskC = () -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return performClaimSession(userId, attempt.getId(), new ClaimSessionRequest(sidC, true));
            };
            Future<MvcResult> fB = pool.submit(taskB);
            Future<MvcResult> fC = pool.submit(taskC);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            MvcResult resB = fB.get(15, TimeUnit.SECONDS);
            MvcResult resC = fC.get(15, TimeUnit.SECONDS);
            assertThat(resB.getResponse().getStatus()).isEqualTo(200);
            assertThat(resC.getResponse().getStatus()).isEqualTo(200);

            UUID winnerSid = attemptRepository.findById(attempt.getId()).orElseThrow().getEditSessionId();
            assertThat(winnerSid).isIn(sidB, sidC);
            UUID loserSid = winnerSid.equals(sidB) ? sidC : sidB;

            assertThat(performHeartbeat(userId, attempt.getId(), winnerSid).getResponse().getStatus())
                    .isEqualTo(200);
            MvcResult loserHb = performHeartbeat(userId, attempt.getId(), loserSid);
            assertThat(loserHb.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(loserHb).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sessionAndHeartbeatEnforceOwnershipAndAccess() throws Exception {
        UUID owner = createStudent("sess-owner");
        UUID stranger = createStudent("sess-stranger");
        UUID manager = createManager("sess-manager");
        GrantOrderResult.Line line = grant(owner, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(owner, "sess-auth", line.entitlementId(), null);

        // Anonymous -> 401
        assertThat(mockMvc.perform(post("/api/v1/me/attempts/{id}/session", attempt.getId()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/me/attempts/{id}/heartbeat", attempt.getId()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        // MANAGE -> 403
        MvcResult mgrSess = mockMvc.perform(
                        post("/api/v1/me/attempts/{id}/session", attempt.getId())
                                .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                .andReturn();
        assertThat(mgrSess.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrSess).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        MvcResult mgrHb = mockMvc.perform(
                        post("/api/v1/me/attempts/{id}/heartbeat", attempt.getId())
                                .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                .andReturn();
        assertThat(mgrHb.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrHb).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        // Other student -> 404
        assertThat(performClaimSession(stranger, attempt.getId(), null).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(performHeartbeat(stranger, attempt.getId(), UUID.randomUUID()).getResponse().getStatus())
                .isEqualTo(404);

        // Closed / TIME_UP attempt rejects session claim with TIME_UP or ATTEMPT_NOT_OPEN
        UUID sid = UUID.randomUUID();
        assertThat(performClaimSession(owner, attempt.getId(), new ClaimSessionRequest(sid, false))
                .getResponse().getStatus()).isEqualTo(200);
        Instant deadline = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId())
                .getFirst().getDeadlineAt();
        mutableClock.setAt(deadline.plusSeconds(5));
        MvcResult expiredClaim = performClaimSession(owner, attempt.getId(), new ClaimSessionRequest(sid, false));
        assertThat(expiredClaim.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(expiredClaim).path("code").asText()).isEqualTo("TIME_UP");
    }

    @Test
    void claimSessionAndHeartbeatRejectStartingAbortedAndSubmitted() throws Exception {
        UUID userId = createStudent("sess-nonopen");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        // 1. After submit, POST /session even with previous sessionId -> 409 ATTEMPT_NOT_OPEN
        Attempt submitted = startAndAwaitOpen(userId, "sess-sub", line.entitlementId(), null);
        UUID sid = claimSession(userId, submitted.getId());
        assertThat(performSubmit(userId, submitted.getId(), "sess-sub-k", sid, null).getResponse().getStatus())
                .isEqualTo(200);
        MvcResult postSubmitClaimSame =
                performClaimSession(userId, submitted.getId(), new ClaimSessionRequest(sid, false));
        assertThat(postSubmitClaimSame.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(postSubmitClaimSame).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        MvcResult postSubmitClaimNull = performClaimSession(userId, submitted.getId(), null);
        assertThat(postSubmitClaimNull.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(postSubmitClaimNull).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");

        // 2. STARTING & ABORTED rows with edit_session_id = NULL -> 409 ATTEMPT_NOT_OPEN on /session and /heartbeat
        Attempt nonOpen = startAndAwaitOpen(userId, "sess-start-abort", line.entitlementId(), null);
        assertThat(nonOpen.getEditSessionId()).isNull();

        jdbc.update("UPDATE attempt.attempt SET state = 'STARTING' WHERE id = ?", nonOpen.getId());
        MvcResult startSession = performClaimSession(userId, nonOpen.getId(), null);
        assertThat(startSession.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(startSession).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        for (UUID hbSid : new UUID[] {null, UUID.randomUUID()}) {
            MvcResult startHb = performHeartbeat(userId, nonOpen.getId(), hbSid);
            assertThat(startHb.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(startHb).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        }

        jdbc.update(
                "UPDATE attempt.attempt SET state = 'ABORTED', abort_reason = 'GENERATION_FAILED' WHERE id = ?",
                nonOpen.getId());
        MvcResult abortedSession =
                performClaimSession(userId, nonOpen.getId(), new ClaimSessionRequest(UUID.randomUUID(), true));
        assertThat(abortedSession.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(abortedSession).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        for (UUID hbSid : new UUID[] {null, UUID.randomUUID()}) {
            MvcResult abortedHb = performHeartbeat(userId, nonOpen.getId(), hbSid);
            assertThat(abortedHb.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(abortedHb).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        }
    }

    @Test
    void claimSessionAndHeartbeatCloseOverdueAttemptsDeterministically() throws Exception {
        UUID userId = createStudent("sess-overdue-lock");
        GrantOrderResult.Line singleLine = grant(userId, "DS-HSA-PART-MATH-MINI", 5, List.of());
        GrantOrderResult.Line fullLine = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 1. PER_PART attempt becomes overdue while POST /session waits on FOR UPDATE (with different sessionId, takeover=false)
            Attempt perPart = startAndAwaitOpen(userId, "sess-perpart-lock", singleLine.entitlementId(), null);
            UUID sidA = claimSession(userId, perPart.getId());
            UUID sidB = UUID.randomUUID();
            Instant ppDeadline =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(perPart.getId()).getFirst().getDeadlineAt();

            mutableClock.setAt(ppDeadline.minusSeconds(1));
            CountDownLatch lockHeld1 = new CountDownLatch(1);
            CountDownLatch releaseLock1 = new CountDownLatch(1);
            Future<Void> holder1 =
                    holdAttemptRowLock(pool, perPart.getId(), lockHeld1, releaseLock1);
            assertThat(lockHeld1.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> claimFuture1 =
                    pool.submit(
                            () ->
                                    performClaimSession(
                                            userId, perPart.getId(), new ClaimSessionRequest(sidB, false)));
            awaitAttemptRowLockWait();
            mutableClock.setAt(ppDeadline.plusSeconds(5));
            releaseLock1.countDown();
            holder1.get(15, TimeUnit.SECONDS);

            MvcResult claimRes1 = claimFuture1.get(15, TimeUnit.SECONDS);
            assertThat(claimRes1.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(claimRes1).path("code").asText()).isEqualTo("TIME_UP");
            Attempt perPartAfter = attemptRepository.findById(perPart.getId()).orElseThrow();
            assertThat(perPartAfter.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(perPartAfter.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(perPartAfter.getFinishedAt()).isEqualTo(ppDeadline);
            assertThat(perPartAfter.getEditSessionId()).isEqualTo(sidA);

            // 2. Multi-part PER_PART Part 0 expires while waiting on FOR UPDATE and throws ATTEMPT_OPEN_ELSEWHERE -> Part 0->1 still commits
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            Attempt multi = startAndAwaitOpen(userId, "sess-multi-lock", fullLine.entitlementId(), null);
            UUID multiSidA = claimSession(userId, multi.getId());
            UUID multiSidB = UUID.randomUUID();
            List<AttemptPart> multiParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(multi.getId());
            Instant p0Deadline = multiParts.get(0).getDeadlineAt();

            mutableClock.setAt(p0Deadline.minusSeconds(1));
            CountDownLatch lockHeld2 = new CountDownLatch(1);
            CountDownLatch releaseLock2 = new CountDownLatch(1);
            Future<Void> holder2 = holdAttemptRowLock(pool, multi.getId(), lockHeld2, releaseLock2);
            assertThat(lockHeld2.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> claimFuture2 =
                    pool.submit(
                            () ->
                                    performClaimSession(
                                            userId, multi.getId(), new ClaimSessionRequest(multiSidB, false)));
            awaitAttemptRowLockWait();
            mutableClock.setAt(p0Deadline.plusSeconds(5));
            releaseLock2.countDown();
            holder2.get(15, TimeUnit.SECONDS);

            MvcResult claimRes2 = claimFuture2.get(15, TimeUnit.SECONDS);
            assertThat(claimRes2.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(claimRes2).path("code").asText()).isEqualTo("ATTEMPT_OPEN_ELSEWHERE");
            List<AttemptPart> multiPartsAfter = attemptPartRepository.findByAttemptIdOrderByOrdAsc(multi.getId());
            assertThat(multiPartsAfter.get(0).getState()).isEqualTo(PartState.CLOSED);
            assertThat(multiPartsAfter.get(0).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(multiPartsAfter.get(0).getClosedAt()).isEqualTo(p0Deadline);
            assertThat(multiPartsAfter.get(1).getState()).isEqualTo(PartState.OPEN);
            assertThat(attemptRepository.findById(multi.getId()).orElseThrow().getEditSessionId())
                    .isEqualTo(multiSidA);

            // 3. AWAITING_CHOICE attempt expires during FOR UPDATE wait on POST /session -> closes CHOICE_TIMEOUT, never 200 or ATTEMPT_OPEN_ELSEWHERE
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            UUID acUser = createStudent("sess-ac-lock");
            GrantOrderResult.Line acLine = grant(acUser, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt acAttempt = startAndAwaitOpen(acUser, "sess-ac-lock", acLine.entitlementId(), null);
            claimSession(acUser, acAttempt.getId());
            List<AttemptPart> acParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(acAttempt.getId());
            assertThat(performFinishPart(acUser, acAttempt.getId(), acParts.get(0).getId(),
                    attemptRepository.findById(acAttempt.getId()).orElseThrow().getEditSessionId(), null)
                    .getResponse().getStatus()).isEqualTo(200);
            Instant choiceDeadline = mutableClock.instant().plus(Duration.ofMinutes(15));
            jdbc.update(
                    "UPDATE attempt.attempt_part SET state = 'CLOSED', close_reason = 'FINISHED', closed_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(mutableClock.instant()),
                    acParts.get(1).getId());
            jdbc.update(
                    "UPDATE attempt.attempt_part SET state = 'AWAITING_CHOICE', choice_deadline_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(choiceDeadline),
                    acParts.get(2).getId());

            mutableClock.setAt(choiceDeadline.minusSeconds(1));
            CountDownLatch lockHeld3 = new CountDownLatch(1);
            CountDownLatch releaseLock3 = new CountDownLatch(1);
            Future<Void> holder3 = holdAttemptRowLock(pool, acAttempt.getId(), lockHeld3, releaseLock3);
            assertThat(lockHeld3.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> claimFuture3 =
                    pool.submit(
                            () ->
                                    performClaimSession(
                                            acUser,
                                            acAttempt.getId(),
                                            new ClaimSessionRequest(UUID.randomUUID(), false)));
            awaitAttemptRowLockWait();
            mutableClock.setAt(choiceDeadline.plusSeconds(5));
            releaseLock3.countDown();
            holder3.get(15, TimeUnit.SECONDS);

            MvcResult claimRes3 = claimFuture3.get(15, TimeUnit.SECONDS);
            assertThat(claimRes3.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(claimRes3).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
            Attempt acAfter = attemptRepository.findById(acAttempt.getId()).orElseThrow();
            assertThat(acAfter.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(acAfter.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
            assertThat(acAfter.getFinishedAt()).isEqualTo(choiceDeadline);

            // 4. POST /heartbeat on overdue attempt: (a) direct past-deadline call & (b) lock-wait expiry
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            Attempt hbDirect = startAndAwaitOpen(userId, "hb-overdue-direct", singleLine.entitlementId(), null);
            UUID hbDirectSid = claimSession(userId, hbDirect.getId());
            Instant hbDirectSeenAt =
                    attemptRepository.findById(hbDirect.getId()).orElseThrow().getEditSessionSeenAt();
            Instant hbDirectDeadline =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(hbDirect.getId()).getFirst().getDeadlineAt();

            mutableClock.setAt(hbDirectDeadline.plusSeconds(5));
            MvcResult hbDirectRes = performHeartbeat(userId, hbDirect.getId(), hbDirectSid);
            assertThat(hbDirectRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(hbDirectRes).path("state").asText()).isEqualTo("GRADING");
            Attempt hbDirectAfter = attemptRepository.findById(hbDirect.getId()).orElseThrow();
            assertThat(hbDirectAfter.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(hbDirectAfter.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(hbDirectAfter.getFinishedAt()).isEqualTo(hbDirectDeadline);
            assertThat(hbDirectAfter.getEditSessionSeenAt()).isEqualTo(hbDirectSeenAt);

            // (b) Lock-wait expiry during POST /heartbeat
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            Attempt hbLock = startAndAwaitOpen(userId, "hb-overdue-lock", singleLine.entitlementId(), null);
            UUID hbLockSid = claimSession(userId, hbLock.getId());
            Instant hbLockSeenAt =
                    attemptRepository.findById(hbLock.getId()).orElseThrow().getEditSessionSeenAt();
            Instant hbLockDeadline =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(hbLock.getId()).getFirst().getDeadlineAt();

            mutableClock.setAt(hbLockDeadline.minusSeconds(1));
            CountDownLatch lockHeld4 = new CountDownLatch(1);
            CountDownLatch releaseLock4 = new CountDownLatch(1);
            Future<Void> holder4 = holdAttemptRowLock(pool, hbLock.getId(), lockHeld4, releaseLock4);
            assertThat(lockHeld4.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> hbFuture =
                    pool.submit(() -> performHeartbeat(userId, hbLock.getId(), hbLockSid));
            awaitAttemptRowLockWait();
            mutableClock.setAt(hbLockDeadline.plusSeconds(5));
            releaseLock4.countDown();
            holder4.get(15, TimeUnit.SECONDS);

            MvcResult hbLockRes = hbFuture.get(15, TimeUnit.SECONDS);
            assertThat(hbLockRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(hbLockRes).path("state").asText()).isEqualTo("GRADING");
            Attempt hbLockAfter = attemptRepository.findById(hbLock.getId()).orElseThrow();
            assertThat(hbLockAfter.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(hbLockAfter.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(hbLockAfter.getFinishedAt()).isEqualTo(hbLockDeadline);
            assertThat(hbLockAfter.getEditSessionSeenAt()).isEqualTo(hbLockSeenAt);

            // (c) Revoked session heartbeat on an overdue attempt throws 409 ATTEMPT_SESSION_REVOKED AND still closes overdue attempt
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            Attempt hbRevoked = startAndAwaitOpen(userId, "hb-overdue-revoked", singleLine.entitlementId(), null);
            claimSession(userId, hbRevoked.getId());
            Instant hbRevokedDeadline =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(hbRevoked.getId()).getFirst().getDeadlineAt();
            mutableClock.setAt(hbRevokedDeadline.plusSeconds(5));
            MvcResult hbRevokedRes = performHeartbeat(userId, hbRevoked.getId(), UUID.randomUUID());
            assertThat(hbRevokedRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(hbRevokedRes).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
            Attempt hbRevokedAfter = attemptRepository.findById(hbRevoked.getId()).orElseThrow();
            assertThat(hbRevokedAfter.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(hbRevokedAfter.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(hbRevokedAfter.getFinishedAt()).isEqualTo(hbRevokedDeadline);
        } finally {
            pool.shutdownNow();
        }
    }

    private Future<Void> holdAttemptRowLock(
            ExecutorService pool,
            UUID attemptId,
            CountDownLatch lockHeld,
            CountDownLatch releaseLock) {
        return pool.submit(
                () -> {
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(
                                    status -> {
                                        attemptRepository.findByIdForUpdate(attemptId).orElseThrow();
                                        lockHeld.countDown();
                                        try {
                                            assertThat(releaseLock.await(15, TimeUnit.SECONDS))
                                                    .isTrue();
                                        } catch (InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(ex);
                                        }
                                    });
                    return null;
                });
    }

    private void awaitAttemptRowLockWait() throws InterruptedException {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Integer waiting =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND pid <> pg_backend_pid()
                              AND state = 'active'
                              AND wait_event_type = 'Lock'
                              AND query ILIKE '%attempt.attempt%'
                            """,
                            Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL FOR UPDATE lock contention");
    }
}
