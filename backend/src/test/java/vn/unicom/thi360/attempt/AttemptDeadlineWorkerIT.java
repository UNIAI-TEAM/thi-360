package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;

/**
 * Integration tests for {@link AttemptDeadlineWorker} covering deadline-anchored closure without
 * connected client devices, chained multi-part expiry, concurrent worker isolation, and profile
 * scheduling guards (AC-10, AC-17, plan B5).
 */
class AttemptDeadlineWorkerIT extends AbstractAttemptDsIT {

    @Autowired private AttemptDeadlineWorker worker;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private EntitlementPort entitlementPort;

    @AfterEach
    void resetSpy() {
        reset(entitlementPort);
    }

    @Test
    void closesAtDeadlineWithoutDevice() throws Exception {
        UUID userId = createStudent("worker-deadline");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "wrk-dead", line.entitlementId(), null);
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        Instant expectedDeadline = p0.getDeadlineAt();
        clearInvocations(entitlementPort);

        // Simulate 5-minute delayed worker sweep with zero HTTP requests.
        mutableClock.setAt(expectedDeadline.plus(Duration.ofMinutes(5)));
        int processed = worker.sweepOverdue();
        assertThat(processed).isEqualTo(1);

        Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(closed.getFinishedAt()).isEqualTo(expectedDeadline);

        AttemptPart closedPart = attemptPartRepository.findById(p0.getId()).orElseThrow();
        assertThat(closedPart.getState()).isEqualTo(PartState.CLOSED);
        assertThat(closedPart.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(closedPart.getClosedAt()).isEqualTo(expectedDeadline);

        verify(entitlementPort, times(1)).confirmUse(attempt.getId());
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void nextPartClockStartsFromDeadline() throws Exception {
        // 1. Single part timeout -> Part 0 closes at part0.deadlineAt, Part 1 opens with startedAt = part0.deadlineAt
        UUID u1 = createStudent("worker-nextpart-1");
        GrantOrderResult.Line line1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = startAndAwaitOpen(u1, "wrk-np-1", line1.entitlementId(), null);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        Instant p0Deadline = a1Parts.get(0).getDeadlineAt();
        int p1Minutes = a1Parts.get(1).getDurationMinutes();

        mutableClock.setAt(p0Deadline.plus(Duration.ofMinutes(2)));
        assertThat(worker.sweepOverdue()).isEqualTo(1);

        List<AttemptPart> a1After = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        assertThat(a1After.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(a1After.get(0).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(a1After.get(0).getClosedAt()).isEqualTo(p0Deadline);

        assertThat(a1After.get(1).getState()).isEqualTo(PartState.OPEN);
        assertThat(a1After.get(1).getStartedAt()).isEqualTo(p0Deadline);
        assertThat(a1After.get(1).getDeadlineAt())
                .isEqualTo(p0Deadline.plus(Duration.ofMinutes(p1Minutes)));
        assertThat(a1After.get(2).getState()).isEqualTo(PartState.PENDING);

        // Close a1 so it does not interfere with next sweep assertion
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);

        // 2. Chained multi-part expiry in a single sweep (advance past both Part 0 and Part 1 deadlines)
        UUID u2 = createStudent("worker-nextpart-chain");
        GrantOrderResult.Line line2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = startAndAwaitOpen(u2, "wrk-np-chain", line2.entitlementId(), null);
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        Instant a2P0Deadline = a2Parts.get(0).getDeadlineAt();
        Instant a2P1Deadline =
                a2P0Deadline.plus(Duration.ofMinutes(a2Parts.get(1).getDurationMinutes()));

        mutableClock.setAt(a2P1Deadline.plus(Duration.ofMinutes(5)));
        int chainedCount = worker.sweepOverdue();
        assertThat(chainedCount).isGreaterThanOrEqualTo(1);

        List<AttemptPart> a2After = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(a2After.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(a2After.get(0).getClosedAt()).isEqualTo(a2P0Deadline);
        assertThat(a2After.get(1).getState()).isEqualTo(PartState.CLOSED);
        assertThat(a2After.get(1).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(a2After.get(1).getStartedAt()).isEqualTo(a2P0Deadline);
        assertThat(a2After.get(1).getClosedAt()).isEqualTo(a2P1Deadline);
        // AT-2b AwaitingChoicePartTransitionHook moves Part 2 to AWAITING_CHOICE with 15m window from closedAt
        assertThat(a2After.get(2).getState()).isEqualTo(PartState.AWAITING_CHOICE);
        assertThat(a2After.get(2).getChoiceDeadlineAt())
                .isEqualTo(a2P1Deadline.plus(Duration.ofMinutes(15)));
        assertThat(attemptRepository.findById(a2.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
    }

    @Test
    void twoWorkersCloseOnce() throws Exception {
        UUID userId = createStudent("worker-skip-lock");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "wrk-2", line.entitlementId(), null);
        Instant deadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst().getDeadlineAt();
        clearInvocations(entitlementPort);

        mutableClock.setAt(deadline.plus(Duration.ofMinutes(3)));

        CountDownLatch worker1InConfirm = new CountDownLatch(1);
        CountDownLatch worker2Finished = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            worker1InConfirm.countDown();
                            assertThat(worker2Finished.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(entitlementPort)
                .confirmUse(attempt.getId());

        CompletableFuture<Integer> worker1 = CompletableFuture.supplyAsync(worker::sweepOverdue);
        assertThat(worker1InConfirm.await(15, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> worker2 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return worker.sweepOverdue();
                            } finally {
                                worker2Finished.countDown();
                            }
                        });

        int count2 = worker2.get(30, TimeUnit.SECONDS);
        int count1 = worker1.get(30, TimeUnit.SECONDS);

        assertThat(count1).isEqualTo(1);
        assertThat(count2).isZero();
        verify(entitlementPort, times(1)).confirmUse(attempt.getId());
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void workerDisabledInTestProfile() throws Exception {
        assertThat(context.getBeansOfType(AttemptDeadlineWorker.Scheduler.class)).isEmpty();

        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                MockWorkerConfig.class, AttemptDeadlineWorker.Scheduler.class);

        runner.withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(ctx -> assertThat(ctx).hasSingleBean(AttemptDeadlineWorker.Scheduler.class));

        runner.withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker", "test");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AttemptDeadlineWorker.Scheduler.class));

        runner.withPropertyValues("thi360.attempt.scheduling.enabled=false")
                .withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AttemptDeadlineWorker.Scheduler.class));

        Method scheduledMethod = AttemptDeadlineWorker.Scheduler.class.getMethod("run");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${thi360.attempt.scheduling.deadline-interval-ms:10000}");
    }

    @Test
    void closesSharedAttemptAtExactDeadlineAndAfterDelay() throws Exception {
        // 1. Exact attempt.deadlineAt on SHARED DS-BCA-FULL-MINI while on Part 1
        UUID u1 = createStudent("worker-shared-exact");
        GrantOrderResult.Line line1 = grant(u1, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt a1 =
                startAndAwaitOpen(
                        u1, "wrk-sh-1", line1.entitlementId(), new PreChoiceInput("CA2", null));
        assertThat(a1.getTiming()).isEqualTo(AttemptTiming.SHARED);
        Instant d1 = a1.getDeadlineAt();
        assertThat(d1).isNotNull();

        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        UUID sid1 = claimSession(u1, a1.getId());
        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(u1, a1.getId(), a1Parts.get(0).getId(), sid1, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        clearInvocations(entitlementPort);

        mutableClock.setAt(d1);
        assertThat(worker.sweepOverdue()).isEqualTo(1);

        Attempt a1Closed = attemptRepository.findById(a1.getId()).orElseThrow();
        assertThat(a1Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a1Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a1Closed.getFinishedAt()).isEqualTo(d1);
        List<AttemptPart> a1PartsAfter = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        assertThat(a1PartsAfter.get(1).getState()).isEqualTo(PartState.CLOSED);
        assertThat(a1PartsAfter.get(1).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(a1PartsAfter.get(1).getClosedAt()).isEqualTo(d1);
        assertThat(a1PartsAfter.get(2).getState()).isEqualTo(PartState.SKIPPED);
        assertThat(a1PartsAfter.get(2).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(a1PartsAfter.get(2).getClosedAt()).isEqualTo(d1);
        verify(entitlementPort, times(1)).confirmUse(a1.getId());
        assertThat(countEntitlementUsage(a1.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a1.getId(), "attempt.finished")).isEqualTo(1);

        // 2. +5m after attempt.deadlineAt on SHARED DS-BCA-FULL-MINI while on Part 1
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("worker-shared-delayed");
        GrantOrderResult.Line line2 = grant(u2, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt a2 =
                startAndAwaitOpen(
                        u2, "wrk-sh-2", line2.entitlementId(), new PreChoiceInput("CA2", null));
        assertThat(a2.getTiming()).isEqualTo(AttemptTiming.SHARED);
        Instant d2 = a2.getDeadlineAt();
        assertThat(d2).isNotNull();

        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        UUID sid2 = claimSession(u2, a2.getId());
        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(u2, a2.getId(), a2Parts.get(0).getId(), sid2, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        clearInvocations(entitlementPort);

        mutableClock.setAt(d2.plus(Duration.ofMinutes(5)));
        assertThat(worker.sweepOverdue()).isEqualTo(1);

        Attempt a2Closed = attemptRepository.findById(a2.getId()).orElseThrow();
        assertThat(a2Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a2Closed.getFinishedAt()).isEqualTo(d2);
        List<AttemptPart> a2PartsAfter = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(a2PartsAfter.get(1).getState()).isEqualTo(PartState.CLOSED);
        assertThat(a2PartsAfter.get(1).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(a2PartsAfter.get(2).getState()).isEqualTo(PartState.SKIPPED);
        assertThat(a2PartsAfter.get(2).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        verify(entitlementPort, times(1)).confirmUse(a2.getId());
        assertThat(countEntitlementUsage(a2.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a2.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void closesAwaitingChoiceAtChoiceDeadline() throws Exception {
        UUID userId = createStudent("worker-awaiting-choice");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "wrk-ac", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
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
        clearInvocations(entitlementPort);

        mutableClock.setAt(choiceDeadline);
        assertThat(worker.sweepOverdue()).isEqualTo(1);

        Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closed.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
        assertThat(closed.getFinishedAt()).isEqualTo(choiceDeadline);
        verify(entitlementPort, times(1)).confirmUse(attempt.getId());
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void sharedWithPastPartDeadlineIgnoredAndPerPartOrderedByEarliestPartDeadline() throws Exception {
        // 1. SHARED attempt whose OPEN part has past part.deadline_at while attempt.deadline_at is future
        UUID uShared = createStudent("worker-poison-pill");
        GrantOrderResult.Line sharedLine = grant(uShared, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt shared =
                startAndAwaitOpen(
                        uShared, "wrk-pill", sharedLine.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> sharedParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(shared.getId());

        Instant now = mutableClock.instant();
        jdbc.update(
                "UPDATE attempt.attempt_part SET deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(now.minusSeconds(60)),
                sharedParts.getFirst().getId());

        assertThat(attemptRepository.hasOverdueDeadline(shared.getId(), now)).isFalse();
        assertThat(attemptRepository.findOverdueAttemptIds(now, 50)).doesNotContain(shared.getId());
        assertThat(worker.sweepOverdue()).isZero();
        assertThat(attemptRepository.findById(shared.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);

        // Close shared attempt before testing PER_PART ordering
        mutableClock.setAt(shared.getDeadlineAt().plusSeconds(10));
        assertThat(worker.sweepOverdue()).isEqualTo(1);

        // 2. findOverdueAttemptIds orders overdue PER_PART attempts by earliest part.deadline_at (not started_at)
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID uEarlierStart = createStudent("worker-order-a");
        GrantOrderResult.Line lineA = grant(uEarlierStart, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a = startAndAwaitOpen(uEarlierStart, "wrk-ord-a", lineA.entitlementId(), null);

        mutableClock.advance(Duration.ofMinutes(5));
        UUID uLaterStart = createStudent("worker-order-b");
        GrantOrderResult.Line lineB = grant(uLaterStart, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt b = startAndAwaitOpen(uLaterStart, "wrk-ord-b", lineB.entitlementId(), null);

        AttemptPart aP0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a.getId()).getFirst();
        AttemptPart bP0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(b.getId()).getFirst();
        Instant aLaterPartDeadline = AttemptTestFixtures.DEFAULT_CLOCK_START.plus(Duration.ofMinutes(40));
        Instant bEarlierPartDeadline = AttemptTestFixtures.DEFAULT_CLOCK_START.plus(Duration.ofMinutes(20));
        jdbc.update(
                "UPDATE attempt.attempt_part SET deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(aLaterPartDeadline),
                aP0.getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(bEarlierPartDeadline),
                bP0.getId());

        Instant checkNow = AttemptTestFixtures.DEFAULT_CLOCK_START.plus(Duration.ofMinutes(45));
        List<UUID> ordered = attemptRepository.findOverdueAttemptIds(checkNow, 10);
        assertThat(ordered).containsExactly(b.getId(), a.getId());
        assertThat(attemptRepository.findOverdueAttemptIdsExcluding(checkNow, Set.of(b.getId()), 10))
                .containsExactly(a.getId());

        // Drain both so subsequent tests start with zero overdue rows
        mutableClock.setAt(checkNow.plus(Duration.ofHours(4)));
        assertThat(worker.sweepOverdue()).isEqualTo(2);
    }

    @Test
    void partialFailureDoesNotAbortRemainingOverdueAttemptsAndTimeoutConfigured() throws Exception {
        TransactionTemplate workerTx =
                (TransactionTemplate) ReflectionTestUtils.getField(worker, "requiresNewTx");
        assertThat(workerTx).isNotNull();
        assertThat(workerTx.getTimeout()).isEqualTo(15);

        UUID u1 = createStudent("worker-fail-a");
        UUID u2 = createStudent("worker-ok-b");
        GrantOrderResult.Line line1 = grant(u1, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line line2 = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());

        Attempt a = startAndAwaitOpen(u1, "wrk-fail-a", line1.entitlementId(), null);
        mutableClock.advance(Duration.ofSeconds(10));
        Attempt b = startAndAwaitOpen(u2, "wrk-ok-b", line2.entitlementId(), null);

        Instant deadlineA = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a.getId()).getFirst().getDeadlineAt();
        Instant deadlineB = attemptPartRepository.findByAttemptIdOrderByOrdAsc(b.getId()).getFirst().getDeadlineAt();
        clearInvocations(entitlementPort);

        doThrow(new RuntimeException("simulated confirmUse failure"))
                .when(entitlementPort)
                .confirmUse(a.getId());

        mutableClock.setAt(deadlineB.plus(Duration.ofMinutes(2)));
        int processed = worker.sweepOverdue();
        assertThat(processed).isEqualTo(1);

        // A rolled back cleanly
        Attempt aAfter = attemptRepository.findById(a.getId()).orElseThrow();
        assertThat(aAfter.getState()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(countEntitlementUsage(a.getId(), "RESERVED")).isEqualTo(1);
        assertThat(countEntitlementUsage(a.getId(), "USED")).isZero();
        assertThat(countOutboxEvents(a.getId(), "attempt.finished")).isZero();

        // B closed cleanly despite A failing earlier in the same sweep
        Attempt bAfter = attemptRepository.findById(b.getId()).orElseThrow();
        assertThat(bAfter.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(bAfter.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(bAfter.getFinishedAt()).isEqualTo(deadlineB);
        assertThat(countEntitlementUsage(b.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(b.getId(), "attempt.finished")).isEqualTo(1);

        // Clear failure and sweep A cleanly so no stray overdue attempts remain
        reset(entitlementPort);
        assertThat(worker.sweepOverdue()).isEqualTo(1);
        assertThat(attemptRepository.findById(a.getId()).orElseThrow().getFinishedAt())
                .isEqualTo(deadlineA);
    }

    @Test
    void lockedRowDoesNotBlockOtherOverdueAttemptsInSameSweep() throws Exception {
        UUID u1 = createStudent("worker-locked-a");
        UUID u2 = createStudent("worker-free-b");
        GrantOrderResult.Line line1 = grant(u1, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line line2 = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());

        Attempt lockedAttempt = startAndAwaitOpen(u1, "wrk-lck-a", line1.entitlementId(), null);
        mutableClock.advance(Duration.ofSeconds(10));
        Attempt freeAttempt = startAndAwaitOpen(u2, "wrk-free-b", line2.entitlementId(), null);
        Instant freeDeadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(freeAttempt.getId()).getFirst().getDeadlineAt();

        mutableClock.setAt(freeDeadline.plus(Duration.ofMinutes(2)));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CompletableFuture<Void> holder =
                CompletableFuture.runAsync(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(lockedAttempt.getId())
                                                            .orElseThrow();
                                                    lockHeld.countDown();
                                                    try {
                                                        assertThat(releaseLock.await(15, TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException ex) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(ex);
                                                    }
                                                }));
        assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(worker.sweepOverdue()).isEqualTo(1);
            assertThat(attemptRepository.findById(lockedAttempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(attemptRepository.findById(freeAttempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.GRADING);
        } finally {
            releaseLock.countDown();
            holder.get(15, TimeUnit.SECONDS);
        }

        assertThat(worker.sweepOverdue()).isEqualTo(1);
        assertThat(attemptRepository.findById(lockedAttempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.GRADING);
    }

    private static class MockWorkerConfig {
        @Bean
        AttemptDeadlineWorker attemptDeadlineWorker() {
            return Mockito.mock(AttemptDeadlineWorker.class);
        }
    }
}
