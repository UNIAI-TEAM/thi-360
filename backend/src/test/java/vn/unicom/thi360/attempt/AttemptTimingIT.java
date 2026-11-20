package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Integration tests for shared deadline expiry, lazy overdue close across read/finish/submit,
 * and exact deadline boundary semantics (AC-7, AC-11, F9, F11).
 */
class AttemptTimingIT extends AbstractAttemptDsIT {

    @Test
    void sharedDeadlineSkipsUnopenedParts() throws Exception {
        UUID userId = createStudent("timing-shared-skip");
        GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt =
                startAndAwaitOpen(
                        userId, "bca-skip", line.entitlementId(), new PreChoiceInput("CA2", null));
        Instant sharedDeadline = attempt.getDeadlineAt();
        assertThat(sharedDeadline).isNotNull();

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        // Finish Part 0 so Part 1 is OPEN and Part 2 is PENDING
        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // Advance past shared deadline -> trigger lazy close -> Part 1 CLOSED(TIME_UP), Part 2 SKIPPED(TIME_UP)
        mutableClock.setAt(sharedDeadline.plus(Duration.ofMinutes(2)));
        MvcResult stateRes = performGetState(userId, attempt.getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(stateRes).path("state").asText()).isEqualTo("GRADING");

        Attempt closedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closedAttempt.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closedAttempt.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(closedAttempt.getFinishedAt()).isEqualTo(sharedDeadline);

        List<AttemptPart> updatedParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(updatedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updatedParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.FINISHED);

        assertThat(updatedParts.get(1).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updatedParts.get(1).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(updatedParts.get(1).getClosedAt()).isEqualTo(sharedDeadline);

        assertThat(updatedParts.get(2).getState()).isEqualTo(PartState.SKIPPED);
        assertThat(updatedParts.get(2).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(updatedParts.get(2).getClosedAt()).isEqualTo(sharedDeadline);

        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void anyRequestClosesOverdueFirst() throws Exception {
        // 1a. GET /me/attempts/{id} lazily closes overdue PER_PART Part 0 at exact part0.deadlineAt
        UUID u1 = createStudent("lazy-get-perpart");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt hsaAttempt = startAndAwaitOpen(u1, "lazy-get-pp", hsaLine.entitlementId(), null);
        List<AttemptPart> hsaParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsaAttempt.getId());
        Instant p0Deadline = hsaParts.get(0).getDeadlineAt();

        mutableClock.setAt(p0Deadline.plus(Duration.ofMinutes(2)));
        MvcResult getPerPartRes = performGetState(u1, hsaAttempt.getId());
        assertThat(getPerPartRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode getPerPartJson = json(getPerPartRes);
        assertThat(getPerPartJson.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(getPerPartJson.path("parts").get(0).path("state").asText()).isEqualTo("CLOSED");
        assertThat(Instant.parse(getPerPartJson.path("parts").get(0).path("closedAt").asText()))
                .isEqualTo(p0Deadline);
        assertThat(getPerPartJson.path("parts").get(1).path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(getPerPartJson.path("parts").get(1).path("startedAt").asText()))
                .isEqualTo(p0Deadline);

        // 1b. GET /me/attempts/{id} lazily closes overdue true SHARED attempt at exact attempt.deadlineAt
        UUID uShared = createStudent("lazy-get-shared-true");
        GrantOrderResult.Line sharedLine = grant(uShared, "DS-VACT-PART-LANGUAGE-MINI", 3, List.of());
        Attempt sharedAttempt = startAndAwaitOpen(uShared, "lazy-get-sh", sharedLine.entitlementId(), null);
        assertThat(sharedAttempt.getTiming()).isEqualTo(AttemptTiming.SHARED);
        Instant sharedDeadline = sharedAttempt.getDeadlineAt();
        assertThat(sharedDeadline).isNotNull();

        mutableClock.setAt(sharedDeadline.plus(Duration.ofMinutes(5)));
        MvcResult getSharedRes = performGetState(uShared, sharedAttempt.getId());
        assertThat(getSharedRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(getSharedRes).path("state").asText()).isEqualTo("GRADING");
        Attempt reloadedShared = attemptRepository.findById(sharedAttempt.getId()).orElseThrow();
        assertThat(reloadedShared.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(reloadedShared.getFinishedAt()).isEqualTo(sharedDeadline);

        // 1c. GET /me/attempts/{id} lazily closes overdue single-part PER_PART attempt at exact part.deadlineAt
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("lazy-get-single-perpart");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 3, List.of());
        Attempt singlePerPart = startAndAwaitOpen(u2, "lazy-get-sp", singleLine.entitlementId(), null);
        assertThat(singlePerPart.getTiming()).isEqualTo(AttemptTiming.PER_PART);
        Instant singlePartDeadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(singlePerPart.getId()).getFirst().getDeadlineAt();

        mutableClock.setAt(singlePartDeadline.plus(Duration.ofMinutes(5)));
        MvcResult getSingleRes = performGetState(u2, singlePerPart.getId());
        assertThat(getSingleRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(getSingleRes).path("state").asText()).isEqualTo("GRADING");
        Attempt reloadedSingle = attemptRepository.findById(singlePerPart.getId()).orElseThrow();
        assertThat(reloadedSingle.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(reloadedSingle.getFinishedAt()).isEqualTo(singlePartDeadline);

        // 2. POST .../parts/{partId}/finish lazily closes overdue part with closed_at = deadline_at, TIME_UP
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u1b = createStudent("lazy-fin-perpart");
        GrantOrderResult.Line hsaLine2 = grant(u1b, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt hsa2 = startAndAwaitOpen(u1b, "lazy-fin-pp", hsaLine2.entitlementId(), null);
        List<AttemptPart> hsa2Parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsa2.getId());
        Instant hsa2P0Deadline = hsa2Parts.get(0).getDeadlineAt();

        mutableClock.setAt(hsa2P0Deadline.plus(Duration.ofMinutes(3)));
        MvcResult finOverdueRes =
                performFinishPart(u1b, hsa2.getId(), hsa2Parts.get(0).getId(), null, null);
        assertThat(finOverdueRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode finJson = json(finOverdueRes);
        assertThat(finJson.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
        assertThat(finJson.path("closedPart").path("closeReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(finJson.path("closedPart").path("closedAt").asText()))
                .isEqualTo(hsa2P0Deadline);
        assertThat(finJson.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(finJson.path("nextPart").path("startedAt").asText()))
                .isEqualTo(hsa2P0Deadline);

        // 2b. Even when finish on last part throws 409 ATTEMPT_PART_IS_LAST, lazy close REQUIRES_NEW still commits!
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt single2 = startAndAwaitOpen(u2, "lazy-fin-last", singleLine.entitlementId(), null);
        AttemptPart single2Part =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single2.getId()).getFirst();
        mutableClock.setAt(single2Part.getDeadlineAt().plus(Duration.ofMinutes(5)));
        MvcResult finLastRes =
                performFinishPart(u2, single2.getId(), single2Part.getId(), null, null);
        assertThat(finLastRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finLastRes).path("code").asText()).isEqualTo("ATTEMPT_PART_IS_LAST");
        Attempt single2Closed = attemptRepository.findById(single2.getId()).orElseThrow();
        assertThat(single2Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(single2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(single2Closed.getFinishedAt()).isEqualTo(single2Part.getDeadlineAt());

        // 3. POST .../submit lazily closes overdue attempt with finished_at = deadline_at, TIME_UP
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt single3 = startAndAwaitOpen(u2, "lazy-sub", singleLine.entitlementId(), null);
        Instant single3Deadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single3.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(single3Deadline.plus(Duration.ofMinutes(5)));
        MvcResult subRes = performSubmit(u2, single3.getId(), "lazy-sub-key", null, null);
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode subJson = json(subRes);
        assertThat(subJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(subJson.path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(subJson.path("finishedAt").asText())).isEqualTo(single3Deadline);
    }

    @Test
    void deadlineBoundaryOneSecondBeforeAtAndAfter() throws Exception {
        // Part A: PER_PART timing exact boundary (deadline_at - 1s / == / +1s)
        UUID userId = createStudent("deadline-boundary");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        // 1. At deadline - 1s -> accepted as SUBMITTED
        Attempt aBefore = startAndAwaitOpen(userId, "bnd-before", line.entitlementId(), null);
        assertThat(aBefore.getTiming()).isEqualTo(AttemptTiming.PER_PART);
        Instant dBefore =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aBefore.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dBefore.minusSeconds(1));
        MvcResult resBefore = performSubmit(userId, aBefore.getId(), "bnd-k-1", null, null);
        assertThat(resBefore.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resBefore).path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(Instant.parse(json(resBefore).path("finishedAt").asText()))
                .isEqualTo(dBefore.minusSeconds(1));

        // 2. At exact deadline -> already closed as TIME_UP at deadline_at
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt aExact = startAndAwaitOpen(userId, "bnd-exact", line.entitlementId(), null);
        Instant dExact =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aExact.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dExact);
        MvcResult resExact = performSubmit(userId, aExact.getId(), "bnd-k-2", null, null);
        assertThat(resExact.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resExact).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(resExact).path("finishedAt").asText())).isEqualTo(dExact);

        // 3. At deadline + 1s -> closed as TIME_UP at deadline_at
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt aAfter = startAndAwaitOpen(userId, "bnd-after", line.entitlementId(), null);
        Instant dAfter =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aAfter.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dAfter.plusSeconds(1));
        MvcResult resAfter = performSubmit(userId, aAfter.getId(), "bnd-k-3", null, null);
        assertThat(resAfter.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resAfter).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(resAfter).path("finishedAt").asText())).isEqualTo(dAfter);

        // Part B: True SHARED timing exact boundary (attempt.deadline_at - 1s / == / +1s, R1-4)
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID sharedUser = createStudent("deadline-boundary-shared");
        GrantOrderResult.Line sharedLine = grant(sharedUser, "DS-VACT-PART-LANGUAGE-MINI", 3, List.of());

        Attempt sBefore = startAndAwaitOpen(sharedUser, "sh-bnd-before", sharedLine.entitlementId(), null);
        assertThat(sBefore.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(sBefore.getDeadlineAt()).isNotNull();
        Instant sdBefore = sBefore.getDeadlineAt();
        mutableClock.setAt(sdBefore.minusSeconds(1));
        MvcResult sResBefore = performSubmit(sharedUser, sBefore.getId(), "sh-bnd-1", null, null);
        assertThat(sResBefore.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(sResBefore).path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(Instant.parse(json(sResBefore).path("finishedAt").asText()))
                .isEqualTo(sdBefore.minusSeconds(1));

        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt sExact = startAndAwaitOpen(sharedUser, "sh-bnd-exact", sharedLine.entitlementId(), null);
        assertThat(sExact.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(sExact.getDeadlineAt()).isNotNull();
        Instant sdExact = sExact.getDeadlineAt();
        mutableClock.setAt(sdExact);
        MvcResult sResExact = performSubmit(sharedUser, sExact.getId(), "sh-bnd-2", null, null);
        assertThat(sResExact.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(sResExact).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(sResExact).path("finishedAt").asText())).isEqualTo(sdExact);

        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt sAfter = startAndAwaitOpen(sharedUser, "sh-bnd-after", sharedLine.entitlementId(), null);
        assertThat(sAfter.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(sAfter.getDeadlineAt()).isNotNull();
        Instant sdAfter = sAfter.getDeadlineAt();
        mutableClock.setAt(sdAfter.plusSeconds(1));
        MvcResult sResAfter = performSubmit(sharedUser, sAfter.getId(), "sh-bnd-3", null, null);
        assertThat(sResAfter.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(sResAfter).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(sResAfter).path("finishedAt").asText())).isEqualTo(sdAfter);
    }

    @Test
    void realCloserWorksInPrepareAndStartOnOverdueAttempt() throws Exception {
        UUID userId = createStudent("real-closer-prep-start");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        // 1. Overdue attempt closed by real AttemptOverdueCloser inside GET /prepare
        Attempt a1 = startAndAwaitOpen(userId, "rc-prep-1", line.entitlementId(), null);
        Instant d1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(d1.plus(Duration.ofMinutes(2)));

        MvcResult prepRes = performPrepare(userId, line.entitlementId());
        assertThat(prepRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode prepJson = json(prepRes);
        assertThat(prepJson.path("openAttempt").isNull()).isTrue();
        assertThat(prepJson.path("attemptsLeft").asInt()).isEqualTo(2);

        Attempt a1Closed = attemptRepository.findById(a1.getId()).orElseThrow();
        assertThat(a1Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a1Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a1Closed.getFinishedAt()).isEqualTo(d1);
        assertThat(countEntitlementUsage(a1.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a1.getId(), "attempt.finished")).isEqualTo(1);

        // 2. Overdue attempt closed by real AttemptOverdueCloser inside POST /me/attempts -> returns 202 new attempt
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt a2 = startAndAwaitOpen(userId, "rc-start-2", line.entitlementId(), null);
        Instant d2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(d2.plus(Duration.ofMinutes(2)));

        MvcResult startRes = performStart(userId, "rc-start-new", line.entitlementId(), null);
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        JsonNode startJson = json(startRes);
        UUID a3Id = UUID.fromString(startJson.path("attemptId").asText());
        assertThat(a3Id).isNotEqualTo(a2.getId());
        assertThat(startJson.path("resumed").asBoolean()).isFalse();
        awaitTerminalStartState(a3Id, AttemptState.IN_PROGRESS);

        Attempt a2Closed = attemptRepository.findById(a2.getId()).orElseThrow();
        assertThat(a2Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a2Closed.getFinishedAt()).isEqualTo(d2);
        assertThat(countEntitlementUsage(a2.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a2.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void concurrentOverdueReadsDoNotExhaustHikariPool() throws Exception {
        int threads = 12;
        List<UUID> users = new ArrayList<>(threads);
        List<UUID> attemptIds = new ArrayList<>(threads);
        Instant maxDeadline = AttemptTestFixtures.DEFAULT_CLOCK_START;

        for (int i = 0; i < threads; i++) {
            UUID userId = createStudent("hikari-conc-" + i);
            GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "hikari-" + i, line.entitlementId(), null);
            Instant d = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst().getDeadlineAt();
            if (d.isAfter(maxDeadline)) {
                maxDeadline = d;
            }
            users.add(userId);
            attemptIds.add(attempt.getId());
        }

        mutableClock.setAt(maxDeadline.plus(Duration.ofMinutes(5)));
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await(15, TimeUnit.SECONDS);
                                    return performGetState(users.get(idx), attemptIds.get(idx));
                                }));
            }

            for (int i = 0; i < threads; i++) {
                MvcResult res = futures.get(i).get(20, TimeUnit.SECONDS);
                assertThat(res.getResponse().getStatus()).isEqualTo(200);
                assertThat(json(res).path("state").asText()).isEqualTo("GRADING");
                Attempt closed = attemptRepository.findById(attemptIds.get(i)).orElseThrow();
                assertThat(closed.getState()).isEqualTo(AttemptState.GRADING);
                assertThat(closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void getStateClosesOverdueAwaitingChoiceAtChoiceDeadline() throws Exception {
        UUID userId = createStudent("lazy-awaiting-choice");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "lazy-ac", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        Instant choiceDeadline = mutableClock.instant().plus(Duration.ofMinutes(15));
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'CLOSED', close_reason = 'FINISHED', closed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(mutableClock.instant()),
                parts.get(1).getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'AWAITING_CHOICE', choice_deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(choiceDeadline),
                parts.get(2).getId());

        mutableClock.setAt(choiceDeadline.plusSeconds(10));
        MvcResult stateRes = performGetState(userId, attempt.getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(stateRes).path("state").asText()).isEqualTo("GRADING");

        Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
        assertThat(closed.getFinishedAt()).isEqualTo(choiceDeadline);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }
}
