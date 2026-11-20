package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.application.AttemptStaleWorker;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.shared.testapi.MutableClock;

/** Integration tests for {@link AttemptStaleWorker} (AC-10, plan B5). */
@Import(AttemptTestFixtures.MutableClockConfig.class)
class AttemptStaleWorkerIT extends AbstractIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired AttemptStaleWorker worker;
    @Autowired AttemptRepository attemptRepository;
    @Autowired AttemptEventRepository attemptEventRepository;
    @Autowired EntitlementGrantService grantService;
    @MockitoSpyBean EntitlementPort entitlementPort;

    private final List<UUID> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.reset(entitlementPort);
        clock.reset();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(entitlementPort);
        clock.reset();
        createdUsers.forEach(userId -> AttemptTestFixtures.cleanupUser(jdbc, userId));
    }

    @Test
    void workerDisabledInTestProfile() throws Exception {
        assertThat(context.getBeansOfType(AttemptStaleWorker.Scheduler.class)).isEmpty();

        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MockWorkerConfig.class, AttemptStaleWorker.Scheduler.class);

        runner.withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).hasSingleBean(AttemptStaleWorker.Scheduler.class));

        runner.withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker", "test");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AttemptStaleWorker.Scheduler.class));

        runner.withPropertyValues("thi360.attempt.scheduling.enabled=false")
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AttemptStaleWorker.Scheduler.class));

        Method scheduledMethod = AttemptStaleWorker.Scheduler.class.getMethod("run");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${thi360.attempt.scheduling.stale-starting-interval-ms:30000}");
    }

    @Test
    void abortsAfterTwoMinutes() {
        UUID userId = student();
        UUID examId = UUID.randomUUID();
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, userId, examId, 2, List.of("FULL"), "FULL_SOLUTION", 1);

        UUID attemptId = UUID.randomUUID();
        entitlementPort.reserve(userId, grant.entitlementId(), attemptId);
        Instant createdAt = clock.instant();
        saveStartingAttempt(attemptId, userId, examId, grant.entitlementId(), grant.grantId(), createdAt);
        Mockito.clearInvocations(entitlementPort);

        // Exactly 120 seconds: NOT stale yet (created_at < now - 2m is false).
        clock.advance(Duration.ofSeconds(120));
        assertThat(worker.sweepStaleStarting()).isZero();
        Attempt at120s = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(at120s.getState()).isEqualTo(AttemptState.STARTING);
        verify(entitlementPort, never()).release(attemptId);

        // 120 seconds + 1 second: stale -> ABORTED(STALE_STARTING) and entitlement released once.
        clock.advance(Duration.ofSeconds(1));
        assertThat(worker.sweepStaleStarting()).isEqualTo(1);

        Attempt aborted = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(aborted.getState()).isEqualTo(AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.STALE_STARTING);
        assertThat(aborted.getAttemptNo()).isNull();

        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        assertThat(events).extracting(AttemptEvent::getType).contains(AttemptEventType.GENERATION_FAILED);

        verify(entitlementPort, times(1)).release(attemptId);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_reserved FROM commerce.entitlement WHERE id = ?",
                Integer.class, grant.entitlementId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.entitlement_usage WHERE attempt_id = ?",
                String.class, attemptId)).isEqualTo("RELEASED");
    }

    @Test
    void twoWorkersReleaseOnce() throws Exception {
        UUID userId = student();
        UUID examId = UUID.randomUUID();
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, userId, examId, 2, List.of("FULL"), "FULL_SOLUTION", 1);

        UUID attemptId = UUID.randomUUID();
        entitlementPort.reserve(userId, grant.entitlementId(), attemptId);
        saveStartingAttempt(attemptId, userId, examId, grant.entitlementId(), grant.grantId(), clock.instant());
        Mockito.clearInvocations(entitlementPort);

        clock.advance(Duration.ofMinutes(3));

        CountDownLatch worker1InRelease = new CountDownLatch(1);
        CountDownLatch worker2Finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            worker1InRelease.countDown();
            assertThat(worker2Finished.await(15, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(entitlementPort).release(attemptId);

        CompletableFuture<Integer> worker1 = CompletableFuture.supplyAsync(worker::sweepStaleStarting);
        assertThat(worker1InRelease.await(15, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> worker2 = CompletableFuture.supplyAsync(() -> {
            try {
                return worker.sweepStaleStarting();
            } finally {
                worker2Finished.countDown();
            }
        });

        int count2 = worker2.get(30, TimeUnit.SECONDS);
        int count1 = worker1.get(30, TimeUnit.SECONDS);

        assertThat(count1).isEqualTo(1);
        assertThat(count2).isZero();
        verify(entitlementPort, times(1)).release(attemptId);

        Attempt reloaded = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(AttemptState.ABORTED);
        assertThat(reloaded.getAbortReason()).isEqualTo(AbortReason.STALE_STARTING);
    }

    private UUID student() {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, "stale.worker");
        createdUsers.add(id);
        return id;
    }

    private void saveStartingAttempt(
            UUID attemptId, UUID userId, UUID examId, UUID entitlementId, UUID grantId, Instant createdAt) {
        Attempt attempt = Attempt.start(
                attemptId,
                userId,
                examId,
                1,
                entitlementId,
                grantId,
                "Stale Worker Exam",
                UUID.randomUUID(),
                "HSA",
                "FULL",
                null,
                null,
                AttemptTiming.PER_PART,
                NavigationMode.SEQUENTIAL,
                Map.of(),
                123L,
                UUID.randomUUID(),
                "FULL_SOLUTION",
                "RAW",
                new BigDecimal("150.0000"),
                new BigDecimal("150.0000"),
                "idem-" + attemptId,
                createdAt);
        attemptRepository.saveAndFlush(attempt);
    }

    private static class MockWorkerConfig {
        @Bean
        AttemptStaleWorker attemptStaleWorker() {
            return Mockito.mock(AttemptStaleWorker.class);
        }
    }
}
