package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.application.FinalAnswerWriter;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
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
import vn.unicom.thi360.commerce.spi.EntitlementPort;

/**
 * Concurrency integration tests proving single-close, single-{@code confirmUse}, and single
 * {@code attempt.finished} outbox publication across submit/worker, submit/submit, and finish/worker
 * races (AC-9, risk concurrency suite).
 */
class AttemptCloseConcurrencyIT extends AbstractAttemptDsIT {

    @Autowired private AttemptDeadlineWorker worker;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private EntitlementPort entitlementPort;
    @MockitoSpyBean private OverdueAttemptCloser overdueCloser;
    @MockitoSpyBean private FinalAnswerWriter finalAnswerWriter;

    @AfterEach
    void resetSpies() {
        reset(entitlementPort, overdueCloser, finalAnswerWriter);
    }

    @Test
    void submitRacingWorkerClosesOnce() throws Exception {
        // Direction A: submit wins lock first while paused inside confirmUse; clock passes deadline; worker skips locked row.
        UUID uA = createStudent("race-submit-first");
        GrantOrderResult.Line lineA = grant(uA, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt aA = startAndAwaitOpen(uA, "race-a", lineA.entitlementId(), null);
        UUID sidA = claimSession(uA, aA.getId());
        Instant deadlineA =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aA.getId()).getFirst().getDeadlineAt();
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        CountDownLatch submitInConfirmUse = new CountDownLatch(1);
        CountDownLatch workerAttempted = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            submitInConfirmUse.countDown();
                            assertThat(workerAttempted.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(entitlementPort)
                .confirmUse(aA.getId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> submitFuture =
                    pool.submit(() -> performSubmit(uA, aA.getId(), "race-a-sub", sidA, null));
            assertThat(submitInConfirmUse.await(15, TimeUnit.SECONDS)).isTrue();

            mutableClock.setAt(deadlineA.plusSeconds(30));
            int workerClosedA = worker.sweepOverdue();
            assertThat(workerClosedA).isZero();
            workerAttempted.countDown();

            MvcResult submitRes = submitFuture.get(15, TimeUnit.SECONDS);
            assertThat(submitRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(submitRes).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(submitRes).path("endReason").asText()).isEqualTo("SUBMITTED");

            verify(entitlementPort, times(1)).confirmUse(aA.getId());
            assertThat(countEntitlementUsage(aA.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(aA.getId(), "attempt.finished")).isEqualTo(1);

            // Direction B: worker wins lock first while paused inside confirmUse; submit waits and returns recorded TIME_UP.
            reset(entitlementPort, overdueCloser, finalAnswerWriter);
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            UUID uB = createStudent("race-worker-first");
            GrantOrderResult.Line lineB = grant(uB, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt aB = startAndAwaitOpen(uB, "race-b", lineB.entitlementId(), null);
            UUID sidB = claimSession(uB, aB.getId());
            Instant deadlineB =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(aB.getId()).getFirst().getDeadlineAt();
            clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

            mutableClock.setAt(deadlineB.plusSeconds(30));
            CountDownLatch workerInConfirmUse = new CountDownLatch(1);
            CountDownLatch submitEnteredCloser = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);

            doAnswer(
                            inv -> {
                                workerInConfirmUse.countDown();
                                assertThat(releaseWorker.await(15, TimeUnit.SECONDS)).isTrue();
                                return inv.callRealMethod();
                            })
                    .when(entitlementPort)
                    .confirmUse(aB.getId());

            doAnswer(
                            inv -> {
                                submitEnteredCloser.countDown();
                                return inv.callRealMethod();
                            })
                    .when(overdueCloser)
                    .closeIfOverdue(eq(aB.getId()), any());

            Future<Integer> workerFuture = pool.submit(() -> worker.sweepOverdue());
            assertThat(workerInConfirmUse.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> submitFutureB =
                    pool.submit(() -> performSubmit(uB, aB.getId(), "race-b-sub", sidB, null));
            assertThat(submitEnteredCloser.await(15, TimeUnit.SECONDS)).isTrue();
            awaitAttemptRowLockWait();
            releaseWorker.countDown();

            assertThat(workerFuture.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            MvcResult submitResB = submitFutureB.get(15, TimeUnit.SECONDS);
            assertThat(submitResB.getResponse().getStatus()).isEqualTo(200);
            JsonNode jsonB = json(submitResB);
            assertThat(jsonB.path("state").asText()).isEqualTo("GRADING");
            assertThat(jsonB.path("endReason").asText()).isEqualTo("TIME_UP");
            assertThat(Instant.parse(jsonB.path("finishedAt").asText())).isEqualTo(deadlineB);

            Attempt finalB = attemptRepository.findById(aB.getId()).orElseThrow();
            assertThat(finalB.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(finalB.getEndReason()).isEqualTo(EndReason.TIME_UP);
            verify(entitlementPort, times(1)).confirmUse(aB.getId());
            assertThat(countEntitlementUsage(aB.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(aB.getId(), "attempt.finished")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoConcurrentSubmitsWithDifferentKeysCloseOnce() throws Exception {
        UUID userId = createStudent("race-two-submits");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "two-sub", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            Callable<MvcResult> task1 =
                    () -> {
                        ready.countDown();
                        assertThat(fire.await(10, TimeUnit.SECONDS)).isTrue();
                        return performSubmit(userId, attempt.getId(), "conc-sub-key-1", sid, null);
                    };
            Callable<MvcResult> task2 =
                    () -> {
                        ready.countDown();
                        assertThat(fire.await(10, TimeUnit.SECONDS)).isTrue();
                        return performSubmit(userId, attempt.getId(), "conc-sub-key-2", sid, null);
                    };
            Future<MvcResult> f1 = pool.submit(task1);
            Future<MvcResult> f2 = pool.submit(task2);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();

            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);
            assertThat(r1.getResponse().getStatus()).isEqualTo(200);
            assertThat(r2.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(r1).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(r2).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(r1).path("endReason").asText()).isEqualTo("SUBMITTED");
            assertThat(json(r2).path("endReason").asText()).isEqualTo("SUBMITTED");

            verify(entitlementPort, times(1)).confirmUse(attempt.getId());
            assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void finishRacingWorkerClosesPartOnce() throws Exception {
        UUID userId = createStudent("race-finish-worker");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "fin-race", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = parts.get(0);
        AttemptPart p1 = parts.get(1);
        Instant p0Deadline = p0.getDeadlineAt();
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        CountDownLatch finishInWriter = new CountDownLatch(1);
        CountDownLatch workerDone = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            finishInWriter.countDown();
                            assertThat(workerDone.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(finalAnswerWriter)
                .writeFinalAnswers(any(), argThat(p -> p != null && p0.getId().equals(p.getId())), any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            mutableClock.setAt(p0Deadline.minusSeconds(1));
            Future<MvcResult> finFuture =
                    pool.submit(
                            () -> performFinishPart(userId, attempt.getId(), p0.getId(), sid, null));
            assertThat(finishInWriter.await(15, TimeUnit.SECONDS)).isTrue();

            mutableClock.setAt(p0Deadline.plus(Duration.ofMinutes(1)));
            int swept = worker.sweepOverdue();
            assertThat(swept).isZero();
            workerDone.countDown();

            MvcResult finRes = finFuture.get(15, TimeUnit.SECONDS);
            assertThat(finRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(finRes).path("closedPart").path("closeReason").asText())
                    .isEqualTo("FINISHED");
            assertThat(json(finRes).path("nextPart").path("state").asText()).isEqualTo("OPEN");

            List<AttemptPart> finalParts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(finalParts.get(0).getState()).isEqualTo(PartState.CLOSED);
            assertThat(finalParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.FINISHED);
            assertThat(finalParts.get(1).getState()).isEqualTo(PartState.OPEN);

            List<AttemptEvent> events =
                    attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId());
            assertThat(
                            events.stream()
                                    .filter(
                                            e ->
                                                    e.getType() == AttemptEventType.PART_CLOSED
                                                            && p0.getId().equals(e.getPartId()))
                                    .count())
                    .isEqualTo(1);
            assertThat(
                            events.stream()
                                    .filter(
                                            e ->
                                                    e.getType() == AttemptEventType.PART_OPENED
                                                            && p1.getId().equals(e.getPartId()))
                                    .count())
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void stalePreLockClockUsesPostLockTime() throws Exception {
        UUID userId = createStudent("stale-prelock-clock");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        // 1. Submit blocked on FOR UPDATE while clock advances past deadline -> re-sampled lockNow rejects late submit as TIME_UP
        Attempt a1 = startAndAwaitOpen(userId, "stale-sub", line.entitlementId(), null);
        UUID sid1 = claimSession(userId, a1.getId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst();
        UUID item1 = attemptItemRepository.findByPartIdOrderByOrdAsc(p1.getId()).getFirst().getId();
        Instant deadline1 = p1.getDeadlineAt();
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        mutableClock.setAt(deadline1.minusSeconds(1));
        CountDownLatch lockHeld1 = new CountDownLatch(1);
        CountDownLatch releaseLock1 = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> lockHolder1 =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    attemptRepository.findByIdForUpdate(a1.getId()).orElseThrow();
                                                    lockHeld1.countDown();
                                                    try {
                                                        assertThat(releaseLock1.await(15, TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException ex) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(ex);
                                                    }
                                                });
                                return null;
                            });
            assertThat(lockHeld1.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> submitFuture =
                    pool.submit(
                            () ->
                                    performSubmit(
                                            userId,
                                            a1.getId(),
                                            "stale-sub-k",
                                            sid1,
                                            new SubmitAttemptRequest(
                                                    List.of(
                                                            new FinalAnswerChange(
                                                                    item1,
                                                                    1L,
                                                                    new AnswerValue.SingleChoice(
                                                                            "late"))))));
            awaitAttemptRowLockWait();

            // Advance clock past deadline while submit waits on FOR UPDATE
            mutableClock.setAt(deadline1.plusSeconds(5));
            releaseLock1.countDown();
            lockHolder1.get(15, TimeUnit.SECONDS);

            MvcResult subRes = submitFuture.get(15, TimeUnit.SECONDS);
            assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode subJson = json(subRes);
            assertThat(subJson.path("state").asText()).isEqualTo("GRADING");
            assertThat(subJson.path("endReason").asText()).isEqualTo("TIME_UP");
            assertThat(Instant.parse(subJson.path("finishedAt").asText())).isEqualTo(deadline1);
            Attempt reloaded1 = attemptRepository.findById(a1.getId()).orElseThrow();
            assertThat(reloaded1.getFinishedAt()).isAfterOrEqualTo(reloaded1.getStartedAt());
            verifyNoInteractions(finalAnswerWriter);

            // 2. Finish throwing 409 ATTEMPT_PART_IS_LAST after clock crosses deadline during FOR UPDATE still commits overdue close
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            Attempt a2 = startAndAwaitOpen(userId, "stale-fin-409", line.entitlementId(), null);
            UUID sid2 = claimSession(userId, a2.getId());
            AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
            Instant deadline2 = p2.getDeadlineAt();

            mutableClock.setAt(deadline2.minusSeconds(1));
            CountDownLatch lockHeld2 = new CountDownLatch(1);
            CountDownLatch releaseLock2 = new CountDownLatch(1);
            Future<Void> lockHolder2 =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    attemptRepository.findByIdForUpdate(a2.getId()).orElseThrow();
                                                    lockHeld2.countDown();
                                                    try {
                                                        assertThat(releaseLock2.await(15, TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException ex) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(ex);
                                                    }
                                                });
                                return null;
                            });
            assertThat(lockHeld2.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> finishFuture =
                    pool.submit(() -> performFinishPart(userId, a2.getId(), p2.getId(), sid2, null));
            awaitAttemptRowLockWait();
            mutableClock.setAt(deadline2.plusSeconds(5));
            releaseLock2.countDown();
            lockHolder2.get(15, TimeUnit.SECONDS);

            MvcResult finRes = finishFuture.get(15, TimeUnit.SECONDS);
            assertThat(finRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(finRes).path("code").asText()).isEqualTo("ATTEMPT_PART_IS_LAST");
            Attempt reloaded2 = attemptRepository.findById(a2.getId()).orElseThrow();
            assertThat(reloaded2.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(reloaded2.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(reloaded2.getFinishedAt()).isEqualTo(deadline2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nonLastFinishPartBlockedAcrossDeadlineUsesPostLockTime() throws Exception {
        UUID userId = createStudent("stale-nonlast-fin");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "stale-nl-fin", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p0 = parts.get(0);
        UUID itemId = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst().getId();
        Instant p0Deadline = p0.getDeadlineAt();
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        mutableClock.setAt(p0Deadline.minusSeconds(1));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> lockHolder =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    attemptRepository.findByIdForUpdate(attempt.getId()).orElseThrow();
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
            assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

            FinishPartRequest request =
                    new FinishPartRequest(
                            List.of(
                                    new FinalAnswerChange(
                                            itemId, 1L, new AnswerValue.SingleChoice("late"))));
            Future<MvcResult> finishFuture =
                    pool.submit(
                            () -> performFinishPart(userId, attempt.getId(), p0.getId(), sid, request));
            awaitAttemptRowLockWait();

            mutableClock.setAt(p0Deadline.plusSeconds(5));
            releaseLock.countDown();
            lockHolder.get(15, TimeUnit.SECONDS);

            MvcResult finRes = finishFuture.get(15, TimeUnit.SECONDS);
            assertThat(finRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode finJson = json(finRes);
            assertThat(finJson.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
            assertThat(finJson.path("closedPart").path("closeReason").asText()).isEqualTo("TIME_UP");
            assertThat(Instant.parse(finJson.path("closedPart").path("closedAt").asText()))
                    .isEqualTo(p0Deadline);
            assertThat(finJson.path("nextPart").path("state").asText()).isEqualTo("OPEN");
            assertThat(Instant.parse(finJson.path("nextPart").path("startedAt").asText()))
                    .isEqualTo(p0Deadline);

            List<AttemptPart> updatedParts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(updatedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
            assertThat(updatedParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(updatedParts.get(0).getClosedAt()).isEqualTo(p0Deadline);
            assertThat(updatedParts.get(1).getState()).isEqualTo(PartState.OPEN);
            assertThat(updatedParts.get(1).getStartedAt()).isEqualTo(p0Deadline);
            assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            verifyNoInteractions(finalAnswerWriter);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void submitPartNotLastBlockedAcrossDeadlinePersistsOverdueClose() throws Exception {
        UUID userId = createStudent("stale-sub-notlast");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "stale-sub-nl", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p0 = parts.get(0);
        Instant p0Deadline = p0.getDeadlineAt();
        clearInvocations(entitlementPort, overdueCloser, finalAnswerWriter);

        mutableClock.setAt(p0Deadline.minusSeconds(1));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> lockHolder =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    attemptRepository.findByIdForUpdate(attempt.getId()).orElseThrow();
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
            assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> submitFuture =
                    pool.submit(
                            () ->
                                    performSubmit(
                                            userId, attempt.getId(), "stale-sub-nl-k", sid, null));
            awaitAttemptRowLockWait();

            mutableClock.setAt(p0Deadline.plusSeconds(5));
            releaseLock.countDown();
            lockHolder.get(15, TimeUnit.SECONDS);

            MvcResult subRes = submitFuture.get(15, TimeUnit.SECONDS);
            assertThat(subRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(subRes).path("code").asText()).isEqualTo("PART_NOT_LAST");

            Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
            assertThat(reloaded.getState()).isEqualTo(AttemptState.IN_PROGRESS);
            List<AttemptPart> updatedParts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(updatedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
            assertThat(updatedParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(updatedParts.get(0).getClosedAt()).isEqualTo(p0Deadline);
            assertThat(updatedParts.get(1).getState()).isEqualTo(PartState.OPEN);
            assertThat(updatedParts.get(1).getStartedAt()).isEqualTo(p0Deadline);
            assertThat(updatedParts.get(2).getState()).isEqualTo(PartState.PENDING);
            verifyNoInteractions(finalAnswerWriter);
        } finally {
            pool.shutdownNow();
        }
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
