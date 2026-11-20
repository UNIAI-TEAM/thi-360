package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort;

/**
 * Integration tests for Part 3 clock start on save, 15-minute choice deadline anchored at Part 2
 * close (early finish and delayed worker), and CHOICE_TIMEOUT boundary/race closure (AC-6..AC-8,
 * regression).
 */
class AttemptChoiceTimingIT extends AbstractAttemptDsIT {

    @Autowired private AttemptDeadlineWorker deadlineWorker;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private EntitlementPort entitlementPort;

    @AfterEach
    void resetSpies() {
        reset(paperSolverPort, entitlementPort);
    }

    @Test
    void partThreeClockStartsWhenReady() throws Exception {
        UUID userId = createStudent("p3-clock-ready");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "clk-ready", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(1).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart p2 = parts.get(2);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                entered.countDown();
                                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            assertThat(
                            performChoice(
                                            userId,
                                            attempt.getId(),
                                            p2.getId(),
                                            "clk-ready-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            mutableClock.advance(Duration.ofMinutes(4));
            Instant expectedSaveTime = mutableClock.instant();
            release.countDown();

            AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(opened.getStartedAt()).isEqualTo(expectedSaveTime);
            assertThat(opened.getDeadlineAt())
                    .isEqualTo(expectedSaveTime.plus(Duration.ofMinutes(opened.getDurationMinutes())));
        } finally {
            release.countDown();
        }
    }

    @Test
    void choiceDeadlineFromPartTwoClose() throws Exception {
        // 1. Early finish of Part 2 sets choice_deadline_at = closed_at + 15m
        UUID u1 = createStudent("ch-dead-early");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = startAndAwaitOpen(u1, "cd-early", l1.entitlementId(), null);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        assertThat(performFinishPart(u1, a1.getId(), a1Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        mutableClock.advance(Duration.ofMinutes(1));
        Instant earlyCloseAt = mutableClock.instant();
        MvcResult fin1 = performFinishPart(u1, a1.getId(), a1Parts.get(1).getId(), null, null);
        assertThat(fin1.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(fin1).path("nextPart").path("state").asText()).isEqualTo("AWAITING_CHOICE");
        AttemptPart a1P2 = attemptPartRepository.findById(a1Parts.get(2).getId()).orElseThrow();
        assertThat(a1P2.getState()).isEqualTo(PartState.AWAITING_CHOICE);
        assertThat(a1P2.getChoiceDeadlineAt()).isEqualTo(earlyCloseAt.plus(Duration.ofMinutes(15)));

        // Close a1 cleanly before second scenario
        jdbc.update("UPDATE attempt.attempt SET state = 'SCORED' WHERE id = ?", a1.getId());

        // 2. Delayed worker close of Part 2 still anchors choice_deadline_at to part2.deadlineAt
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("ch-dead-worker");
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = startAndAwaitOpen(u2, "cd-wrk", l2.entitlementId(), null);
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(performFinishPart(u2, a2.getId(), a2Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        AttemptPart a2P1 = attemptPartRepository.findById(a2Parts.get(1).getId()).orElseThrow();
        Instant p1Deadline = a2P1.getDeadlineAt();
        mutableClock.setAt(p1Deadline.plus(Duration.ofMinutes(4)));
        assertThat(deadlineWorker.sweepOverdue()).isEqualTo(1);

        AttemptPart a2P1After = attemptPartRepository.findById(a2P1.getId()).orElseThrow();
        AttemptPart a2P2After = attemptPartRepository.findById(a2Parts.get(2).getId()).orElseThrow();
        assertThat(a2P1After.getClosedAt()).isEqualTo(p1Deadline);
        assertThat(a2P2After.getState()).isEqualTo(PartState.AWAITING_CHOICE);
        assertThat(a2P2After.getChoiceDeadlineAt())
                .isEqualTo(p1Deadline.plus(Duration.ofMinutes(15)));
    }

    @Test
    void choiceTimeoutClosesAttempt() throws Exception {
        // 1. Boundary: choice_deadline_at - 1s is accepted
        UUID u1 = createStudent("ch-bnd-ok");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = advanceToAwaitingChoice(u1, "ch-ok", l1.entitlementId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        mutableClock.setAt(p1.getChoiceDeadlineAt().minusSeconds(1));
        MvcResult okRes =
                performChoice(
                        u1,
                        a1.getId(),
                        p1.getId(),
                        "bnd-ok",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(okRes.getResponse().getStatus()).isEqualTo(202);
        awaitPartState(p1.getId(), PartState.OPEN);

        // 2. Exact choice_deadline_at on choice endpoint -> 409 CHOICE_WINDOW_CLOSED + lazy close commits CHOICE_TIMEOUT
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("ch-bnd-exact");
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = advanceToAwaitingChoice(u2, "ch-exact", l2.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        Instant deadline2 = p2.getChoiceDeadlineAt();
        clearInvocations(entitlementPort);

        mutableClock.setAt(deadline2);
        MvcResult expiredRes =
                performChoice(
                        u2,
                        a2.getId(),
                        p2.getId(),
                        "bnd-exp",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(expiredRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(expiredRes).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

        Attempt a2Closed = attemptRepository.findById(a2.getId()).orElseThrow();
        assertThat(a2Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a2Closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
        assertThat(a2Closed.getFinishedAt()).isEqualTo(deadline2);
        AttemptPart p2Closed = attemptPartRepository.findById(p2.getId()).orElseThrow();
        assertThat(p2Closed.getState()).isEqualTo(PartState.SKIPPED);
        assertThat(p2Closed.getCloseReason()).isEqualTo(PartCloseReason.CHOICE_TIMEOUT);
        verify(entitlementPort, times(1)).confirmUse(a2.getId());
        assertThat(countEntitlementUsage(a2.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a2.getId(), "attempt.finished")).isEqualTo(1);

        // 3. Worker closes overdue AWAITING_CHOICE at choice_deadline_at
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u3 = createStudent("ch-wrk-timeout");
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a3 = advanceToAwaitingChoice(u3, "ch-wrk", l3.entitlementId());
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);
        Instant deadline3 = p3.getChoiceDeadlineAt();
        clearInvocations(entitlementPort);

        mutableClock.setAt(deadline3.plusSeconds(5));
        assertThat(deadlineWorker.sweepOverdue()).isEqualTo(1);
        Attempt a3Closed = attemptRepository.findById(a3.getId()).orElseThrow();
        assertThat(a3Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a3Closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
        assertThat(a3Closed.getFinishedAt()).isEqualTo(deadline3);
        assertThat(attemptPartRepository.findById(p3.getId()).orElseThrow().getState())
                .isEqualTo(PartState.SKIPPED);
        verify(entitlementPort, times(1)).confirmUse(a3.getId());
        assertThat(countEntitlementUsage(a3.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a3.getId(), "attempt.finished")).isEqualTo(1);

        // 4. Race between choice and AttemptDeadlineWorker at deadline -> only one wins
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u4 = createStudent("ch-race");
        GrantOrderResult.Line l4 = grant(u4, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a4 = advanceToAwaitingChoice(u4, "ch-race", l4.entitlementId());
        AttemptPart p4 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a4.getId()).get(2);
        Instant deadline4 = p4.getChoiceDeadlineAt();
        clearInvocations(entitlementPort);

        // Case 4a: choice enters tx while lock is held at deadline - 1s; clock moves to deadline before lock release -> worker vs choice
        mutableClock.setAt(deadline4);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> choiceFuture =
                    pool.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return performChoice(
                                        u4,
                                        a4.getId(),
                                        p4.getId(),
                                        "race-ch-k",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null));
                            });
            Future<Integer> workerFuture =
                    pool.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return deadlineWorker.sweepOverdue();
                            });

            MvcResult raceChoiceRes = choiceFuture.get(15, TimeUnit.SECONDS);
            workerFuture.get(15, TimeUnit.SECONDS);
            assertThat(raceChoiceRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(raceChoiceRes).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");
            assertThat(countEntitlementUsage(a4.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(a4.getId(), "attempt.finished")).isEqualTo(1);
            verify(entitlementPort, times(1)).confirmUse(a4.getId());
        } finally {
            pool.shutdownNow();
        }

        // Case 4b: worker holds SKIP LOCKED wait / lock contention while choice confirms just before deadline -> choice wins, worker skips/does not close
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u5 = createStudent("ch-race-win");
        GrantOrderResult.Line l5 = grant(u5, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a5 = advanceToAwaitingChoice(u5, "ch-race-win", l5.entitlementId());
        AttemptPart p5 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a5.getId()).get(2);
        Instant deadline5 = p5.getChoiceDeadlineAt();

        mutableClock.setAt(deadline5.minusSeconds(1));
        CountDownLatch confirmInLock = new CountDownLatch(1);
        CountDownLatch workerDone = new CountDownLatch(1);
        ExecutorService pool2 = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> lockAttemptAndWait =
                    CompletableFuture.runAsync(
                            () ->
                                    new TransactionTemplate(transactionManager)
                                            .executeWithoutResult(
                                                    s -> {
                                                        attemptRepository
                                                                .findByIdForUpdate(a5.getId())
                                                                .orElseThrow();
                                                        confirmInLock.countDown();
                                                        try {
                                                            assertThat(
                                                                            workerDone.await(
                                                                                    10,
                                                                                    TimeUnit.SECONDS))
                                                                    .isTrue();
                                                        } catch (InterruptedException e) {
                                                            Thread.currentThread().interrupt();
                                                        }
                                                    }),
                            pool2);
            assertThat(confirmInLock.await(10, TimeUnit.SECONDS)).isTrue();

            mutableClock.setAt(deadline5);
            // Worker tries sweepOverdue while row is locked -> SKIP LOCKED skips it -> 0!
            assertThat(deadlineWorker.sweepOverdue()).isZero();
            workerDone.countDown();
            lockAttemptAndWait.get(10, TimeUnit.SECONDS);

            // Now confirm at deadline - 1s succeeds, and subsequent worker sweep at deadline leaves attempt IN_PROGRESS
            mutableClock.setAt(deadline5.minusSeconds(1));
            assertThat(
                            performChoice(
                                            u5,
                                            a5.getId(),
                                            p5.getId(),
                                            "race-win-k",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState(p5.getId(), PartState.OPEN);
            mutableClock.setAt(deadline5);
            assertThat(deadlineWorker.sweepOverdue()).isZero();
            assertThat(attemptRepository.findById(a5.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
        } finally {
            workerDone.countDown();
            pool2.shutdownNow();
        }
    }

    private Attempt advanceToAwaitingChoice(UUID userId, String keyPrefix, UUID entitlementId)
            throws Exception {
        Attempt attempt = startAndAwaitOpen(userId, keyPrefix, entitlementId, null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(1).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        return attempt;
    }
}
