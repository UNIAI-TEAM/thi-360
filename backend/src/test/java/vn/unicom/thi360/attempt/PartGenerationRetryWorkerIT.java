package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.application.AttemptGenerationProperties;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.attempt.application.PartGenerationRetryWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;

/**
 * Integration tests for {@link PartGenerationRetryWorker} covering 2-minute stuck GENERATING
 * recovery, 30s/2m/5m exponential retry delays, 3rd-failure Ops ERROR alert + attempt retention,
 * and worker profile scheduling/executor activation (AC-12..AC-14, regression).
 */
class PartGenerationRetryWorkerIT extends AbstractAttemptDsIT {

    @Autowired private PartGenerationRetryWorker worker;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void stuckPartFailsAfterTwoMinutes() throws Exception {
        UUID userId = createStudent("stuck-part-2m");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "stk", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

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
                                            "stk-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            AttemptPart generating = attemptPartRepository.findById(p2.getId()).orElseThrow();
            assertThat(generating.getState()).isEqualTo(PartState.GENERATING);
            Instant generatingSince = generating.getGeneratingSince();

            // Exact 120 seconds: NOT failed
            mutableClock.setAt(generatingSince.plusSeconds(120));
            assertThat(worker.failStuckGenerating()).isZero();
            assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.GENERATING);

            // 120 seconds + 1 second: failed as STALE_GENERATING
            mutableClock.setAt(generatingSince.plusSeconds(121));
            assertThat(worker.failStuckGenerating()).isEqualTo(1);

            AttemptPart failed = attemptPartRepository.findById(p2.getId()).orElseThrow();
            assertThat(failed.getState()).isEqualTo(PartState.GENERATION_FAILED);
            assertThat(failed.getGenerationError()).isEqualTo("STALE_GENERATING");
            assertThat(failed.getGenerationTries()).isEqualTo(1);
            assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()))
                    .anyMatch(
                            e ->
                                    e.getType() == AttemptEventType.GENERATION_FAILED
                                            && p2.getId().equals(e.getPartId()));
        } finally {
            release.countDown();
        }
    }

    @Test
    void retriesAtScheduledDelays() throws Exception {
        UUID userId = createStudent("retry-delays");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "rdel", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "always fails");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        // Failure #1
        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "rdel-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        AttemptPart f1 = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(f1.getGenerationTries()).isEqualTo(1);
        Instant tFail1 = lastFailedEventAt(attempt.getId(), p2.getId());

        // Retry #1: 29s -> no, 30s -> yes
        mutableClock.setAt(tFail1.plusSeconds(29));
        assertThat(worker.retryFailedParts()).isZero();
        mutableClock.setAt(tFail1.plusSeconds(30));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        AttemptPart f2 = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(f2.getGenerationTries()).isEqualTo(2);
        Instant tFail2 = lastFailedEventAt(attempt.getId(), p2.getId());

        // Retry #2: 1m59s -> no, 2m00s -> yes
        mutableClock.setAt(tFail2.plusSeconds(119));
        assertThat(worker.retryFailedParts()).isZero();
        mutableClock.setAt(tFail2.plusSeconds(120));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        AttemptPart f3 = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(f3.getGenerationTries()).isEqualTo(3);
        Instant tFail3 = lastFailedEventAt(attempt.getId(), p2.getId());

        // Retry #3: 4m59s -> no, 5m00s -> yes
        mutableClock.setAt(tFail3.plusSeconds(299));
        assertThat(worker.retryFailedParts()).isZero();
        mutableClock.setAt(tFail3.plusSeconds(300));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        AttemptPart f4 = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(f4.getGenerationTries()).isEqualTo(4);

        // After 4th failure: no more automatic retries even +1h later
        mutableClock.advance(Duration.ofHours(1));
        assertThat(worker.retryFailedParts()).isZero();
        assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getGenerationTries())
                .isEqualTo(4);
    }

    @Test
    void thirdConsecutiveFailureAlertsOps() throws Exception {
        attemptStaysOpenAfterFailures();
    }

    @Test
    void attemptStaysOpenAfterFailures() throws Exception {
        Logger jobLogger = (Logger) LoggerFactory.getLogger("thi360.job");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        jobLogger.addAppender(appender);

        try {
            UUID userId = createStudent("ops-alert-3rd");
            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt attempt = advanceToAwaitingChoice(userId, "ops3", line.entitlementId());
            AttemptPart p2 =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

            doAnswer(
                            inv -> {
                                List<String> partCodes = inv.getArgument(2);
                                if (partCodes.contains(p2.getPartCode())) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "boom");
                                }
                                return inv.callRealMethod();
                            })
                    .when(paperSolverPort)
                    .generate(any(), anyInt(), any(), any(), anyLong(), any());

            // Failure 1 -> 0 ERROR logs
            assertThat(
                            performChoice(
                                            userId,
                                            attempt.getId(),
                                            p2.getId(),
                                            "ops-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            assertThat(errorEventsForAttempt(appender, attempt.getId())).isEmpty();

            // Failure 2 -> 0 ERROR logs
            mutableClock.advance(Duration.ofSeconds(30));
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            assertThat(errorEventsForAttempt(appender, attempt.getId())).isEmpty();

            // Failure 3 -> EXACTLY 1 ERROR log on thi360.job
            mutableClock.advance(Duration.ofMinutes(2));
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            List<ILoggingEvent> errorsAt3 = errorEventsForAttempt(appender, attempt.getId());
            assertThat(errorsAt3).hasSize(1);
            String msg = errorsAt3.getFirst().getFormattedMessage();
            assertThat(msg)
                    .contains(attempt.getId().toString())
                    .contains(p2.getId().toString())
                    .contains(p2.getPartCode())
                    .contains(attempt.getExamId().toString())
                    .contains("DS-HSA-FULL-MINI")
                    .contains("versionNo=" + attempt.getExamVersionNo())
                    .contains("generationTries=3")
                    .contains("GENERATION_FAILED");

            // Failure 4 -> STILL exactly 1 ERROR log, attempt stays IN_PROGRESS & RESERVED
            mutableClock.advance(Duration.ofMinutes(5));
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            assertThat(errorEventsForAttempt(appender, attempt.getId())).hasSize(1);
            assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);

            // Success on manual retry resets generationTries to 0
            reset(paperSolverPort);
            assertThat(
                            performRetry(
                                            userId,
                                            attempt.getId(),
                                            p2.getId(),
                                            "ops-recover",
                                            null,
                                            new RetryPartRequest("recovered"))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(opened.getGenerationTries()).isZero();
            assertThat(opened.getGenerationError()).isNull();
        } finally {
            jobLogger.detachAppender(appender);
        }
    }

    @Test
    void executorRunsInWorkerProfile() {
        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                WorkerContextConfig.class,
                                GenerationExecutor.class,
                                PartGenerationRetryWorker.Scheduler.class);

        runner.withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(
                        ctx -> {
                            assertThat(
                                            ctx.getEnvironment()
                                                    .getProperty("spring.main.web-application-type"))
                                    .isEqualTo("none");
                            assertThat(ctx).hasSingleBean(PartGenerationRetryWorker.Scheduler.class);
                            GenerationExecutor executor = ctx.getBean(GenerationExecutor.class);
                            CountDownLatch ran = new CountDownLatch(1);
                            AtomicBoolean busy = new AtomicBoolean(false);
                            executor.submit(ran::countDown, () -> busy.set(true));
                            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
                            assertThat(busy).isFalse();
                        });
    }

    @Test
    void workerDisabledInTestProfile() throws Exception {
        assertThat(context.getBeansOfType(PartGenerationRetryWorker.Scheduler.class)).isEmpty();

        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                MockWorkerConfig.class, PartGenerationRetryWorker.Scheduler.class);

        runner.withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(PartGenerationRetryWorker.Scheduler.class);
                            assertThat(
                                            ctx.getEnvironment()
                                                    .getProperty("spring.main.web-application-type"))
                                    .isEqualTo(WebApplicationType.NONE.name().toLowerCase());
                        });

        runner.withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker", "test");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PartGenerationRetryWorker.Scheduler.class));

        runner.withPropertyValues("thi360.attempt.scheduling.enabled=false")
                .withInitializer(
                        ctx -> {
                            ctx.getEnvironment().setActiveProfiles("worker");
                            ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                        })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PartGenerationRetryWorker.Scheduler.class));

        Method scheduledMethod = PartGenerationRetryWorker.Scheduler.class.getMethod("run");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${thi360.attempt.scheduling.part-retry-interval-ms:30000}");
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

    private Instant lastFailedEventAt(UUID attemptId, UUID partId) {
        return attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId).stream()
                .filter(
                        e ->
                                e.getType() == AttemptEventType.GENERATION_FAILED
                                        && partId.equals(e.getPartId()))
                .map(AttemptEvent::getAt)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static List<ILoggingEvent> errorEventsForAttempt(
            ListAppender<ILoggingEvent> appender, UUID attemptId) {
        return appender.list.stream()
                .filter(
                        ev ->
                                ev.getLevel() == Level.ERROR
                                        && ev.getFormattedMessage().contains(attemptId.toString()))
                .toList();
    }

    private static class MockWorkerConfig {
        @Bean
        PartGenerationRetryWorker partGenerationRetryWorker() {
            return Mockito.mock(PartGenerationRetryWorker.class);
        }
    }

    private static class WorkerContextConfig {
        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        AttemptGenerationProperties attemptGenerationProperties() {
            return new AttemptGenerationProperties(
                    2, 16, Duration.ofSeconds(60), Duration.ofMinutes(2));
        }

        @Bean
        PartGenerationRetryWorker partGenerationRetryWorker() {
            return Mockito.mock(PartGenerationRetryWorker.class);
        }
    }
}
