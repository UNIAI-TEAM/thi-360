package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
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
        // AT-2a PartTransitionHook.NOOP leaves Part 2 PENDING and attempt IN_PROGRESS (AT-2b adds AWAITING_CHOICE)
        assertThat(a2After.get(2).getState()).isEqualTo(PartState.PENDING);
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

    private static class MockWorkerConfig {
        @Bean
        AttemptDeadlineWorker attemptDeadlineWorker() {
            return Mockito.mock(AttemptDeadlineWorker.class);
        }
    }
}
