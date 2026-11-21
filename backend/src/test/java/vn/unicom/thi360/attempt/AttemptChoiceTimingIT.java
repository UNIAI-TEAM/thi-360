package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.attempt.application.PartGenerationRetryWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;

/**
 * Integration tests for Part 3 clock start on save, 15-minute choice deadline anchored at Part 2
 * close (early finish and delayed worker), and CHOICE_TIMEOUT boundary/race closure (AC-6..AC-8,
 * regression).
 */
@TestPropertySource(
        properties = {
            "thi360.attempt.generation.threads=1",
            "thi360.attempt.generation.queue-capacity=4",
            "thi360.attempt.generation.max-queue-wait=60s"
        })
class AttemptChoiceTimingIT extends AbstractAttemptDsIT {

    @Autowired private AttemptDeadlineWorker deadlineWorker;
    @Autowired private PartGenerationRetryWorker retryWorker;
    @Autowired private GenerationExecutor generationExecutor;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private EntitlementPort entitlementPort;
    @MockitoSpyBean private IdempotentCommand idempotentCommand;

    @AfterEach
    void resetSpies() {
        reset(paperSolverPort, entitlementPort, idempotentCommand);
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

        // Case 4b: true choice-vs-worker concurrency race (choice acquires FOR UPDATE at deadline - 1s, pauses in tx,
        //          clock hits deadline, worker sweeps via SKIP LOCKED -> 0, choice resumes -> 202 + OPEN)
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u5 = createStudent("ch-race-win");
        GrantOrderResult.Line l5 = grant(u5, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a5 = advanceToAwaitingChoice(u5, "ch-race-win", l5.entitlementId());
        AttemptPart p5 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a5.getId()).get(2);
        Instant deadline5 = p5.getChoiceDeadlineAt();

        mutableClock.setAt(deadline5.minusSeconds(1));
        CountDownLatch enteredLock = new CountDownLatch(1);
        CountDownLatch releaseChoice = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            enteredLock.countDown();
                            assertThat(releaseChoice.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(entitlementPort)
                .benefitsOf(a5.getId());

        ExecutorService pool2 = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> choiceFuture =
                    pool2.submit(
                            () ->
                                    performChoice(
                                            u5,
                                            a5.getId(),
                                            p5.getId(),
                                            "race-win-k",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(enteredLock.await(10, TimeUnit.SECONDS)).isTrue();

            mutableClock.setAt(deadline5);
            assertThat(deadlineWorker.sweepOverdue()).isZero();

            releaseChoice.countDown();
            MvcResult winRes = choiceFuture.get(15, TimeUnit.SECONDS);
            assertThat(winRes.getResponse().getStatus()).isEqualTo(202);
            awaitPartState(p5.getId(), PartState.OPEN);
            assertThat(deadlineWorker.sweepOverdue()).isZero();
            assertThat(attemptRepository.findById(a5.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
        } finally {
            releaseChoice.countDown();
            pool2.shutdownNow();
            reset(entitlementPort);
        }

        // Case 5: Lock-boundary expiry proof: pre-tx closeIfOverdue runs at deadline - 1s (no-op),
        //         executeConfirmChoice blocks on FOR UPDATE, clock moves to deadline before lock release
        //         -> 409 CHOICE_WINDOW_CLOSED AND attempt committed as GRADING / CHOICE_TIMEOUT + SKIPPED
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u6 = createStudent("ch-lock-bnd");
        GrantOrderResult.Line l6 = grant(u6, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a6 = advanceToAwaitingChoice(u6, "ch-lkb", l6.entitlementId());
        AttemptPart p6 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a6.getId()).get(2);
        Instant deadline6 = p6.getChoiceDeadlineAt();
        clearInvocations(entitlementPort);

        mutableClock.setAt(deadline6.minusSeconds(1));
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch choiceInIdempotent = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            choiceInIdempotent.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.choice"), eq(a6.getId()), any(), any(), any());

        ExecutorService pool3 = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder =
                    pool3.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a6.getId())
                                                            .orElseThrow();
                                                    holderLocked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedChoice =
                    pool3.submit(
                            () ->
                                    performChoice(
                                            u6,
                                            a6.getId(),
                                            p6.getId(),
                                            "lock-bnd-k",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(choiceInIdempotent.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(deadline6);
            releaseHolder.countDown();
            holder.get(10, TimeUnit.SECONDS);

            MvcResult bndRes = blockedChoice.get(15, TimeUnit.SECONDS);
            assertThat(bndRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(bndRes).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

            Attempt a6Closed = attemptRepository.findById(a6.getId()).orElseThrow();
            assertThat(a6Closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(a6Closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
            assertThat(a6Closed.getFinishedAt()).isEqualTo(deadline6);
            AttemptPart p6Closed = attemptPartRepository.findById(p6.getId()).orElseThrow();
            assertThat(p6Closed.getState()).isEqualTo(PartState.SKIPPED);
            assertThat(p6Closed.getCloseReason()).isEqualTo(PartCloseReason.CHOICE_TIMEOUT);
            verify(entitlementPort, times(1)).confirmUse(a6.getId());
            assertThat(countEntitlementUsage(a6.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(a6.getId(), "attempt.finished")).isEqualTo(1);
        } finally {
            releaseHolder.countDown();
            pool3.shutdownNow();
            reset(idempotentCommand);
        }
    }

    @Test
    void inLockOverdueTransitionsBetweenPreTxAndForUpdate() throws Exception {
        // (a) Part 2 expires while choice request waits for FOR UPDATE -> Part 3 transitions AWAITING_CHOICE in lock -> 202 + OPEN
        UUID u1 = createStudent("lkb-p2-exp");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = startAndAwaitOpen(u1, "lkb-p2", l1.entitlementId(), null);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        assertThat(performFinishPart(u1, a1.getId(), a1Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart p1 = attemptPartRepository.findById(a1Parts.get(1).getId()).orElseThrow();
        AttemptPart p2 = attemptPartRepository.findById(a1Parts.get(2).getId()).orElseThrow();
        Instant p1Deadline = p1.getDeadlineAt();

        mutableClock.setAt(p1Deadline.minusSeconds(1));
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch choiceEntered = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            choiceEntered.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.choice"), eq(a1.getId()), any(), any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a1.getId())
                                                            .orElseThrow();
                                                    holderLocked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedChoice =
                    pool.submit(
                            () ->
                                    performChoice(
                                            u1,
                                            a1.getId(),
                                            p2.getId(),
                                            "lkb-p2-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(choiceEntered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(p1Deadline);
            releaseHolder.countDown();
            holder.get(10, TimeUnit.SECONDS);

            MvcResult okRes = blockedChoice.get(15, TimeUnit.SECONDS);
            assertThat(okRes.getResponse().getStatus()).isEqualTo(202);
            AttemptPart p2Opened = awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(p2Opened.getChoiceDeadlineAt())
                    .isEqualTo(p1Deadline.plus(Duration.ofMinutes(15)));
            AttemptPart p1Closed = attemptPartRepository.findById(p1.getId()).orElseThrow();
            assertThat(p1Closed.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p1Closed.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(p1Closed.getClosedAt()).isEqualTo(p1Deadline);
        } finally {
            releaseHolder.countDown();
            pool.shutdownNow();
            reset(idempotentCommand);
        }

        // (b) OPEN Part 3 expires while same-choice resend with NEW key waits for FOR UPDATE -> 409 PART_NOT_AWAITING + commits closed attempt
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("lkb-p3-exp");
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = advanceToAwaitingChoice(u2, "lkb-p3", l2.entitlementId());
        AttemptPart a2P2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        assertThat(
                        performChoice(
                                        u2,
                                        a2.getId(),
                                        a2P2.getId(),
                                        "lkb-p3-init",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        AttemptPart openP2 = awaitPartState(a2P2.getId(), PartState.OPEN);
        Instant p2Deadline = openP2.getDeadlineAt();

        mutableClock.setAt(p2Deadline.minusSeconds(1));
        CountDownLatch holder2Locked = new CountDownLatch(1);
        CountDownLatch releaseHolder2 = new CountDownLatch(1);
        CountDownLatch resendEntered = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            resendEntered.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.choice"), eq(a2.getId()), any(), any(), any());

        ExecutorService pool2 = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder2 =
                    pool2.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a2.getId())
                                                            .orElseThrow();
                                                    holder2Locked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder2.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holder2Locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedResend =
                    pool2.submit(
                            () ->
                                    performChoice(
                                            u2,
                                            a2.getId(),
                                            a2P2.getId(),
                                            "lkb-p3-new-key",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(resendEntered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(p2Deadline);
            releaseHolder2.countDown();
            holder2.get(10, TimeUnit.SECONDS);

            MvcResult expRes = blockedResend.get(15, TimeUnit.SECONDS);
            assertThat(expRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(expRes).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

            Attempt a2Closed = attemptRepository.findById(a2.getId()).orElseThrow();
            assertThat(a2Closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(a2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(a2Closed.getFinishedAt()).isEqualTo(p2Deadline);
            AttemptPart p2Closed = attemptPartRepository.findById(a2P2.getId()).orElseThrow();
            assertThat(p2Closed.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p2Closed.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        } finally {
            releaseHolder2.countDown();
            pool2.shutdownNow();
            reset(idempotentCommand);
        }

        // (c) >15m lock-wait cascade: PENDING Part 3 waits on FOR UPDATE while clock jumps to p1Deadline + 15m ->
        //     advanceOverdueInLock closes Part 2 (TIME_UP) then Part 3 (CHOICE_TIMEOUT) -> post-advanceOverdueInLock throws 409 CHOICE_WINDOW_CLOSED
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u3 = createStudent("lkb-cascade-15m");
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a3 = startAndAwaitOpen(u3, "lkb-cas", l3.entitlementId(), null);
        List<AttemptPart> a3Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId());
        assertThat(performFinishPart(u3, a3.getId(), a3Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a3P1 = attemptPartRepository.findById(a3Parts.get(1).getId()).orElseThrow();
        AttemptPart a3P2 = attemptPartRepository.findById(a3Parts.get(2).getId()).orElseThrow();
        Instant a3P1Deadline = a3P1.getDeadlineAt();
        Instant cascadeChoiceDeadline = a3P1Deadline.plus(Duration.ofMinutes(15));

        mutableClock.setAt(a3P1Deadline.minusSeconds(1));
        CountDownLatch holder3Locked = new CountDownLatch(1);
        CountDownLatch releaseHolder3 = new CountDownLatch(1);
        CountDownLatch cascadeEntered = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            cascadeEntered.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.choice"), eq(a3.getId()), any(), any(), any());

        ExecutorService pool3 = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder3 =
                    pool3.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a3.getId())
                                                            .orElseThrow();
                                                    holder3Locked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder3.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holder3Locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedCascade =
                    pool3.submit(
                            () ->
                                    performChoice(
                                            u3,
                                            a3.getId(),
                                            a3P2.getId(),
                                            "lkb-cas-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(cascadeEntered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(cascadeChoiceDeadline);
            releaseHolder3.countDown();
            holder3.get(10, TimeUnit.SECONDS);

            MvcResult cascadeRes = blockedCascade.get(15, TimeUnit.SECONDS);
            assertThat(cascadeRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(cascadeRes).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

            Attempt a3Closed = attemptRepository.findById(a3.getId()).orElseThrow();
            assertThat(a3Closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(a3Closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
            assertThat(a3Closed.getFinishedAt()).isEqualTo(cascadeChoiceDeadline);
            AttemptPart a3P1Closed = attemptPartRepository.findById(a3P1.getId()).orElseThrow();
            assertThat(a3P1Closed.getState()).isEqualTo(PartState.CLOSED);
            assertThat(a3P1Closed.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(a3P1Closed.getClosedAt()).isEqualTo(a3P1Deadline);
            AttemptPart a3P2Closed = attemptPartRepository.findById(a3P2.getId()).orElseThrow();
            assertThat(a3P2Closed.getState()).isEqualTo(PartState.SKIPPED);
            assertThat(a3P2Closed.getCloseReason()).isEqualTo(PartCloseReason.CHOICE_TIMEOUT);
            assertThat(countEntitlementUsage(a3.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(a3.getId(), "attempt.finished")).isEqualTo(1);
        } finally {
            releaseHolder3.countDown();
            pool3.shutdownNow();
            reset(idempotentCommand);
        }
    }

    @Test
    void sharedDeadlineGuardsOnGenerationAndRetry() throws Exception {
        // 1. persistGeneratedPart discards without writing items or usage if SHARED deadline_at <= now
        UUID u1 = createStudent("sh-guard-gen");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = advanceToAwaitingChoice(u1, "shg-1", l1.entitlementId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode())) {
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
                                            u1,
                                            a1.getId(),
                                            p1.getId(),
                                            "shg-1-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            Instant sharedDeadline1 = mutableClock.instant().plus(Duration.ofMinutes(2));
            jdbc.update(
                    "UPDATE attempt.attempt SET timing = 'SHARED', deadline_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(sharedDeadline1),
                    a1.getId());
            Integer ledgerBefore =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM questionbank.question_usage_ledger WHERE exam_id = ? AND kind = 'ATTEMPT'",
                            Integer.class,
                            a1.getExamId());

            mutableClock.setAt(sharedDeadline1);
            release.countDown();
            CountDownLatch sentinel = new CountDownLatch(1);
            AtomicBoolean sentinelBusy = new AtomicBoolean(false);
            generationExecutor.submit(
                    sentinel::countDown,
                    () -> {
                        sentinelBusy.set(true);
                        sentinel.countDown();
                    });
            assertThat(sentinel.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(sentinelBusy.get()).isFalse();

            assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(p1.getId())).isEmpty();
            Integer ledgerAfter =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM questionbank.question_usage_ledger WHERE exam_id = ? AND kind = 'ATTEMPT'",
                            Integer.class,
                            a1.getExamId());
            assertThat(ledgerAfter).isEqualTo(ledgerBefore);
            assertThat(attemptPartRepository.findById(p1.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.GENERATING);
            assertThat(deadlineWorker.sweepOverdue()).isEqualTo(1);
        } finally {
            release.countDown();
            reset(paperSolverPort);
        }

        // 2. retryFailedById skips and executeRetry rejects when SHARED deadline_at <= now under lock
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("sh-guard-ret");
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = advanceToAwaitingChoice(u2, "shg-2", l2.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);

        AtomicBoolean failFirst = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())
                                    && failFirst.compareAndSet(true, false)) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED, "fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        assertThat(
                        performChoice(
                                        u2,
                                        a2.getId(),
                                        p2.getId(),
                                        "shg-2-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        Instant sharedDeadline2 = mutableClock.instant().plusSeconds(45);
        jdbc.update(
                "UPDATE attempt.attempt SET timing = 'SHARED', deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(sharedDeadline2),
                a2.getId());

        // Advance past both 30s backoff AND sharedDeadline2: retryWorker.retryFailedParts() must return 0!
        mutableClock.setAt(sharedDeadline2);
        assertThat(retryWorker.retryFailedParts()).isZero();
        assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getState())
                .isEqualTo(PartState.GENERATION_FAILED);

        // And manual retry under lock (blocked until clock hits sharedDeadline2) returns 409 PART_NOT_AWAITING + closes attempt
        mutableClock.setAt(sharedDeadline2.minusSeconds(1));
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch retryEntered = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            retryEntered.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.retry"), eq(a2.getId()), any(), any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a2.getId())
                                                            .orElseThrow();
                                                    holderLocked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedRetry =
                    pool.submit(
                            () ->
                                    performRetry(
                                            u2,
                                            a2.getId(),
                                            p2.getId(),
                                            "shg-2-ret",
                                            null,
                                            new RetryPartRequest("late shared retry")));
            assertThat(retryEntered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(sharedDeadline2);
            releaseHolder.countDown();
            holder.get(10, TimeUnit.SECONDS);

            MvcResult lateRetryRes = blockedRetry.get(15, TimeUnit.SECONDS);
            assertThat(lateRetryRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(lateRetryRes).path("code").asText()).isEqualTo("PART_NOT_AWAITING");
            Attempt a2Closed = attemptRepository.findById(a2.getId()).orElseThrow();
            assertThat(a2Closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(a2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        } finally {
            releaseHolder.countDown();
            pool.shutdownNow();
            reset(idempotentCommand);
        }

        // 3. R2-3: SHARED deadline <= choiceDeadlineAt under FOR UPDATE at lockNow >= choiceDeadlineAt
        //    throws 409 PART_NOT_AWAITING, commits EndReason.TIME_UP, and invokes confirmUse once
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u3 = createStudent("sh-prec-choice");
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a3 = advanceToAwaitingChoice(u3, "shg-3", l3.entitlementId());
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);
        Instant choiceDeadline3 = p3.getChoiceDeadlineAt();
        Instant sharedDeadline3 = choiceDeadline3.minus(Duration.ofMinutes(5));
        jdbc.update(
                "UPDATE attempt.attempt SET timing = 'SHARED', deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(sharedDeadline3),
                a3.getId());
        clearInvocations(entitlementPort);

        mutableClock.setAt(sharedDeadline3.minusSeconds(1));
        CountDownLatch holder3Locked = new CountDownLatch(1);
        CountDownLatch releaseHolder3 = new CountDownLatch(1);
        CountDownLatch choice3Entered = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            choice3Entered.countDown();
                            return inv.callRealMethod();
                        })
                .when(idempotentCommand)
                .execute(any(), eq("attempt.part.choice"), eq(a3.getId()), any(), any(), any());

        ExecutorService pool3 = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder3 =
                    pool3.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(a3.getId())
                                                            .orElseThrow();
                                                    holder3Locked.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseHolder3.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                return null;
                            });
            assertThat(holder3Locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> blockedChoice3 =
                    pool3.submit(
                            () ->
                                    performChoice(
                                            u3,
                                            a3.getId(),
                                            p3.getId(),
                                            "shg-3-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null)));
            assertThat(choice3Entered.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOnPostgresLock();

            mutableClock.setAt(choiceDeadline3);
            releaseHolder3.countDown();
            holder3.get(10, TimeUnit.SECONDS);

            MvcResult sharedPrecRes = blockedChoice3.get(15, TimeUnit.SECONDS);
            assertThat(sharedPrecRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(sharedPrecRes).path("code").asText()).isEqualTo("PART_NOT_AWAITING");
            Attempt a3Closed = attemptRepository.findById(a3.getId()).orElseThrow();
            assertThat(a3Closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(a3Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(a3Closed.getFinishedAt()).isEqualTo(sharedDeadline3);
            verify(entitlementPort, times(1)).confirmUse(a3.getId());
        } finally {
            releaseHolder3.countDown();
            pool3.shutdownNow();
            reset(idempotentCommand);
        }
    }

    private void awaitBlockedOnPostgresLock() throws InterruptedException {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Integer waiting =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND wait_event_type = 'Lock'
                            """,
                            Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(15);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL lock contention");
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
