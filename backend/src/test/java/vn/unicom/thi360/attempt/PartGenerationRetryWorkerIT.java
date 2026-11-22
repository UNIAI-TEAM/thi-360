package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import vn.unicom.thi360.exam.spi.PublishedExamPort;

/**
 * Integration tests for {@link PartGenerationRetryWorker} covering 2-minute stuck GENERATING
 * recovery, 30s/2m/5m exponential retry delays, 3rd-failure Ops ERROR alert + attempt retention,
 * SQL backoff non-starvation, executor capacity capping/saturation short-circuit, sweep exception
 * isolation, {@code SKIP LOCKED}, and worker profile scheduling (AC-12..AC-14, regression).
 */
@TestPropertySource(
        properties = {
            "thi360.attempt.generation.threads=1",
            "thi360.attempt.generation.queue-capacity=1",
            "thi360.attempt.generation.max-queue-wait=60s"
        })
class PartGenerationRetryWorkerIT extends AbstractAttemptDsIT {

    @Autowired private PartGenerationRetryWorker worker;
    @Autowired private GenerationExecutor generationExecutor;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private PublishedExamPort publishedExamPortSpy;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort, publishedExamPortSpy);
    }

    private record AttemptSession(Attempt attempt, UUID sessionId) {}

    @Test
    void stuckPartFailsAfterTwoMinutes() throws Exception {
        UUID userId = createStudent("stuck-part-2m");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        AttemptSession session = advanceToAwaitingChoice(userId, "stk", line.entitlementId());
        Attempt attempt = session.attempt();
        UUID sid = session.sessionId();
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
                                            sid,
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
        AttemptSession session = advanceToAwaitingChoice(userId, "rdel", line.entitlementId());
        Attempt attempt = session.attempt();
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
                                        session.sessionId(),
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
        Logger jobLogger = (Logger) LoggerFactory.getLogger("thi360.job");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        jobLogger.addAppender(appender);

        try {
            UUID userId = createStudent("ops-alert-3rd");
            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
            AttemptSession session = advanceToAwaitingChoice(userId, "ops3", line.entitlementId());
            Attempt attempt = session.attempt();
            AttemptPart p2 =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

            doAnswer(
                            inv -> {
                                List<String> partCodes = inv.getArgument(2);
                                if (partCodes.contains(p2.getPartCode())) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "boom-detail");
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
                                            session.sessionId(),
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

            // Failure 3 -> EXACTLY 1 ERROR log on thi360.job including detail, and 3 GENERATION_FAILED events with full ops payload
            mutableClock.advance(Duration.ofMinutes(2));
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            List<ILoggingEvent> errorsAt3 = errorEventsForAttempt(appender, attempt.getId());
            assertThat(errorsAt3).hasSize(1);
            ILoggingEvent alertEvent = errorsAt3.getFirst();
            String msg = alertEvent.getFormattedMessage();
            assertThat(msg)
                    .contains(attempt.getId().toString())
                    .contains(p2.getId().toString())
                    .contains(p2.getPartCode())
                    .contains(attempt.getExamId().toString())
                    .contains("DS-HSA-FULL-MINI")
                    .contains("versionNo=" + attempt.getExamVersionNo())
                    .contains("generationTries=3")
                    .contains("GENERATION_FAILED")
                    .contains("detail=boom-detail");
            assertThat(alertEvent.getKeyValuePairs())
                    .anySatisfy(
                            kv -> {
                                assertThat(kv.key).isEqualTo("detail");
                                assertThat(kv.value).isEqualTo("boom-detail");
                            });

            List<AttemptEvent> failedEvents =
                    attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()).stream()
                            .filter(
                                    e ->
                                            e.getType() == AttemptEventType.GENERATION_FAILED
                                                    && p2.getId().equals(e.getPartId()))
                            .toList();
            assertThat(failedEvents).hasSize(3);
            for (int i = 0; i < 3; i++) {
                Map<String, Object> data = failedEvents.get(i).getData();
                assertThat(data)
                        .containsEntry("attemptId", attempt.getId().toString())
                        .containsEntry("partId", p2.getId().toString())
                        .containsEntry("partCode", p2.getPartCode())
                        .containsEntry("examId", attempt.getExamId().toString())
                        .containsEntry("examCode", "DS-HSA-FULL-MINI")
                        .containsEntry("versionNo", attempt.getExamVersionNo())
                        .containsEntry("reason", "GENERATION_FAILED")
                        .containsEntry("generationTries", i + 1)
                        .containsEntry("detail", "boom-detail");
            }

            // Failure 4 -> still strictly 1 ERROR log
            mutableClock.advance(Duration.ofMinutes(5));
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
            assertThat(errorEventsForAttempt(appender, attempt.getId())).hasSize(1);
        } finally {
            jobLogger.detachAppender(appender);
        }
    }

    @Test
    void attemptStaysOpenAfterFailures() throws Exception {
        UUID userId = createStudent("stay-open-fail");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        AttemptSession session = advanceToAwaitingChoice(userId, "so-fail", line.entitlementId());
        Attempt attempt = session.attempt();
        UUID sid = session.sessionId();
        AttemptPart p2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_TIMEOUT,
                                        "timeout");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "so-ch",
                                        sid,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        mutableClock.advance(Duration.ofSeconds(30));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        mutableClock.advance(Duration.ofMinutes(2));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        mutableClock.advance(Duration.ofMinutes(5));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        AttemptPart f4 = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(f4.getGenerationTries()).isEqualTo(4);
        assertThat(f4.getGenerationError()).isEqualTo("GENERATION_TIMEOUT");

        // Even +1 hour later, attempt remains IN_PROGRESS & RESERVED (never closed or billed)
        mutableClock.advance(Duration.ofHours(1));
        assertThat(worker.retryFailedParts()).isZero();
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isZero();

        // Manual retry succeeds once solver recovers and resets generationTries to 0
        reset(paperSolverPort);
        assertThat(
                        performRetry(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "so-recover",
                                        sid,
                                        new RetryPartRequest("recovered"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
        assertThat(opened.getGenerationTries()).isZero();
        assertThat(opened.getGenerationError()).isNull();
    }

    @Test
    void duePartsNotStarvedBehindBackoff() throws Exception {
        // threads=1, queueCapacity=1 -> batchLimit=2. Place 2 earlier attempts into 2m backoff
        // (not yet due) ahead of 1 newer due attempt -> SQL backoff filter selects the due attempt!
        UUID u1 = createStudent("starve-1");
        UUID u2 = createStudent("starve-2");
        UUID u3 = createStudent("starve-due");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());

        AttemptSession s1 = advanceToAwaitingChoice(u1, "st1", l1.entitlementId());
        AttemptSession s2 = advanceToAwaitingChoice(u2, "st2", l2.entitlementId());
        AttemptSession s3 = advanceToAwaitingChoice(u3, "st3", l3.entitlementId());
        Attempt a1 = s1.attempt();
        Attempt a2 = s2.attempt();
        Attempt a3 = s3.attempt();
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);

        AtomicBoolean failAll = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode()) && failAll.get()) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "initial fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        // t=0: a1 and a2 fail try #1
        assertThat(
                        performChoice(
                                        u1,
                                        a1.getId(),
                                        p1.getId(),
                                        "st1-ch",
                                        s1.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p1.getId(), PartState.GENERATION_FAILED);
        assertThat(
                        performChoice(
                                        u2,
                                        a2.getId(),
                                        p2.getId(),
                                        "st2-ch",
                                        s2.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        // t=+35s: a1 and a2 fail try #2 (entering 2m backoff until t=+155s)
        mutableClock.advance(Duration.ofSeconds(35));
        assertThat(worker.retryFailedParts()).isEqualTo(2);
        awaitPartState(p1.getId(), PartState.GENERATION_FAILED);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(attemptPartRepository.findById(p1.getId()).orElseThrow().getGenerationTries())
                .isEqualTo(2);
        assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getGenerationTries())
                .isEqualTo(2);

        // t=+40s: newer a3 fails try #1 (30s backoff -> due at t=+70s while a1/a2 are still in 2m backoff)
        mutableClock.advance(Duration.ofSeconds(5));
        assertThat(
                        performChoice(
                                        u3,
                                        a3.getId(),
                                        p3.getId(),
                                        "st3-ch",
                                        s3.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p3.getId(), PartState.GENERATION_FAILED);

        // Allow generation to succeed at t=+70s; only a3 is due -> not starved behind a1, a2
        failAll.set(false);
        mutableClock.advance(Duration.ofSeconds(30));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(p3.getId(), PartState.OPEN);
        assertThat(attemptPartRepository.findById(p1.getId()).orElseThrow().getState())
                .isEqualTo(PartState.GENERATION_FAILED);
        assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getState())
                .isEqualTo(PartState.GENERATION_FAILED);

        // Clean up remaining failed parts before next test
        mutableClock.advance(Duration.ofMinutes(3));
        assertThat(worker.retryFailedParts()).isEqualTo(2);
        awaitPartState(p1.getId(), PartState.OPEN);
        awaitPartState(p2.getId(), PartState.OPEN);
    }

    @Test
    void retryOrdersByOldestLastFailedAtBeforeChoiceConfirmedAt() throws Exception {
        // threads=1, queueCapacity=1 -> batchLimit=2. When 3 due failed parts exist and
        // earliest-confirmed p1 failed MORE recently than p2/p3, p2 & p3 retry first.
        UUID u1 = createStudent("ord-p1");
        UUID u2 = createStudent("ord-p2");
        UUID u3 = createStudent("ord-p3");
        AttemptSession s1 =
                advanceToAwaitingChoice(
                        u1,
                        "ord1",
                        grant(u1, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession s2 =
                advanceToAwaitingChoice(
                        u2,
                        "ord2",
                        grant(u2, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession s3 =
                advanceToAwaitingChoice(
                        u3,
                        "ord3",
                        grant(u3, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        Attempt a1 = s1.attempt();
        Attempt a2 = s2.attempt();
        Attempt a3 = s3.attempt();
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);

        AtomicBoolean failAll = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode()) && failAll.get()) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "ord-fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        Instant t0 = mutableClock.instant();
        assertThat(
                        performChoice(
                                        u1,
                                        a1.getId(),
                                        p1.getId(),
                                        "ord1-ch",
                                        s1.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p1.getId(), PartState.GENERATION_FAILED);

        mutableClock.setAt(t0.plusSeconds(10));
        assertThat(
                        performChoice(
                                        u2,
                                        a2.getId(),
                                        p2.getId(),
                                        "ord2-ch",
                                        s2.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        mutableClock.setAt(t0.plusSeconds(15));
        assertThat(
                        performChoice(
                                        u3,
                                        a3.getId(),
                                        p3.getId(),
                                        "ord3-ch",
                                        s3.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p3.getId(), PartState.GENERATION_FAILED);

        // At t0+30s only p1 is due (try #1 -> try #2), giving p1 earliest choice_confirmed_at
        // (t0) yet newest last_failed_at (t0+30s vs t0+10s / t0+15s).
        mutableClock.setAt(t0.plusSeconds(30));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        AttemptPart p1Fail2 = awaitPartState(p1.getId(), PartState.GENERATION_FAILED);
        assertThat(p1Fail2.getGenerationTries()).isEqualTo(2);
        assertThat(lastFailedEventAt(a1.getId(), p1.getId())).isEqualTo(t0.plusSeconds(30));

        // At t0+150s all 3 are due; batchLimit=2 must pick oldest last_failed_at (p2 & p3), not p1.
        failAll.set(false);
        mutableClock.setAt(t0.plusSeconds(150));
        assertThat(worker.retryFailedParts()).isEqualTo(2);
        awaitPartState(p2.getId(), PartState.OPEN);
        awaitPartState(p3.getId(), PartState.OPEN);

        AttemptPart p1Untouched = attemptPartRepository.findById(p1.getId()).orElseThrow();
        assertThat(p1Untouched.getState()).isEqualTo(PartState.GENERATION_FAILED);
        assertThat(p1Untouched.getGenerationTries()).isEqualTo(2);

        // Next sweep picks up p1 cleanly
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(p1.getId(), PartState.OPEN);
    }

    @Test
    void sweepCapsToExecutorCapacityAndStopsOnBusy() throws Exception {
        // 1. Capacity cap (threads=1 + queueCapacity=1 = 2): 3 due parts -> sweep dispatches 2, 3rd untouched
        UUID u1 = createStudent("cap-1");
        UUID u2 = createStudent("cap-2");
        UUID u3 = createStudent("cap-3");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());

        AttemptSession s1 = advanceToAwaitingChoice(u1, "cp1", l1.entitlementId());
        AttemptSession s2 = advanceToAwaitingChoice(u2, "cp2", l2.entitlementId());
        AttemptSession s3 = advanceToAwaitingChoice(u3, "cp3", l3.entitlementId());
        Attempt a1 = s1.attempt();
        Attempt a2 = s2.attempt();
        Attempt a3 = s3.attempt();
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);

        AtomicBoolean failInitial = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode()) && failInitial.get()) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_TIMEOUT,
                                        "cap-init-fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        for (Object[] row :
                List.of(
                        new Object[] {u1, a1.getId(), p1.getId(), "cp1-ch", s1.sessionId()},
                        new Object[] {u2, a2.getId(), p2.getId(), "cp2-ch", s2.sessionId()},
                        new Object[] {u3, a3.getId(), p3.getId(), "cp3-ch", s3.sessionId()})) {
            assertThat(
                            performChoice(
                                            (UUID) row[0],
                                            (UUID) row[1],
                                            (UUID) row[2],
                                            (String) row[3],
                                            (UUID) row[4],
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState((UUID) row[2], PartState.GENERATION_FAILED);
        }

        failInitial.set(false);
        mutableClock.advance(Duration.ofSeconds(35));
        assertThat(worker.retryFailedParts()).isEqualTo(2);
        awaitPartState(p1.getId(), PartState.OPEN);
        awaitPartState(p2.getId(), PartState.OPEN);

        AttemptPart p3Untouched = attemptPartRepository.findById(p3.getId()).orElseThrow();
        assertThat(p3Untouched.getState()).isEqualTo(PartState.GENERATION_FAILED);
        assertThat(p3Untouched.getGenerationTries()).isEqualTo(1);
        assertThat(p3Untouched.getGenerationError()).isEqualTo("GENERATION_TIMEOUT");

        // Next tick picks up p3 cleanly
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(p3.getId(), PartState.OPEN);

        // 2. Synchronous saturation short-circuit: fill thread + 1-slot queue -> first due hits GENERATION_BUSY,
        //    second due part is NOT attempted so its generationTries stays unchanged without GENERATION_BUSY!
        reset(paperSolverPort);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID bu1 = createStudent("busy-blk1");
        UUID bu2 = createStudent("busy-blk2");
        UUID du1 = createStudent("busy-due1");
        UUID du2 = createStudent("busy-due2");
        AttemptSession bs1 = advanceToAwaitingChoice(bu1, "bb1", grant(bu1, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession bs2 = advanceToAwaitingChoice(bu2, "bb2", grant(bu2, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession ds1 = advanceToAwaitingChoice(du1, "bd1", grant(du1, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession ds2 = advanceToAwaitingChoice(du2, "bd2", grant(du2, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        Attempt ba1 = bs1.attempt();
        Attempt ba2 = bs2.attempt();
        Attempt da1 = ds1.attempt();
        Attempt da2 = ds2.attempt();
        AttemptPart bp1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(ba1.getId()).get(2);
        AttemptPart bp2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(ba2.getId()).get(2);
        AttemptPart dp1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(da1.getId()).get(2);
        AttemptPart dp2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(da2.getId()).get(2);

        AtomicBoolean failDueSetup = new AtomicBoolean(true);
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(dp1.getPartCode())) {
                                if (failDueSetup.get()) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_TIMEOUT,
                                            "pre-fail");
                                }
                                blockerEntered.countDown();
                                assertThat(releaseBlocker.await(15, TimeUnit.SECONDS)).isTrue();
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            assertThat(
                            performChoice(
                                            du1,
                                            da1.getId(),
                                            dp1.getId(),
                                            "bd1-ch",
                                            ds1.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState(dp1.getId(), PartState.GENERATION_FAILED);
            assertThat(
                            performChoice(
                                            du2,
                                            da2.getId(),
                                            dp2.getId(),
                                            "bd2-ch",
                                            ds2.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState(dp2.getId(), PartState.GENERATION_FAILED);

            failDueSetup.set(false);
            mutableClock.advance(Duration.ofSeconds(35));

            // Saturate thread + 1-slot queue
            assertThat(
                            performChoice(
                                            bu1,
                                            ba1.getId(),
                                            bp1.getId(),
                                            "bb1-ch",
                                            bs1.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(blockerEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(
                            performChoice(
                                            bu2,
                                            ba2.getId(),
                                            bp2.getId(),
                                            "bb2-ch",
                                            bs2.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);

            // Worker sweep sees onBusy on dp1 -> stops immediately -> dp2 untouched!
            assertThat(worker.retryFailedParts()).isEqualTo(1);
            AttemptPart dp1After = attemptPartRepository.findById(dp1.getId()).orElseThrow();
            AttemptPart dp2After = attemptPartRepository.findById(dp2.getId()).orElseThrow();
            assertThat(dp1After.getGenerationError()).isEqualTo("GENERATION_BUSY");
            assertThat(dp1After.getGenerationTries()).isEqualTo(2);
            assertThat(dp2After.getState()).isEqualTo(PartState.GENERATION_FAILED);
            assertThat(dp2After.getGenerationError()).isEqualTo("GENERATION_TIMEOUT");
            assertThat(dp2After.getGenerationTries()).isEqualTo(1);

            releaseBlocker.countDown();
            awaitPartState(bp1.getId(), PartState.OPEN);
            awaitPartState(bp2.getId(), PartState.OPEN);
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void sweepProcessesStuckAndDueWithIsolationAndSkipLocked() throws Exception {
        // 1. worker.sweep() processes 1 stuck GENERATING (>2m) + 1 due GENERATION_FAILED -> returns 2
        UUID uStuck = createStudent("swp-stuck");
        UUID uDue = createStudent("swp-due");
        AttemptSession sStuck =
                advanceToAwaitingChoice(
                        uStuck,
                        "swp-s",
                        grant(uStuck, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession sDue =
                advanceToAwaitingChoice(
                        uDue,
                        "swp-d",
                        grant(uDue, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        Attempt aStuck = sStuck.attempt();
        Attempt aDue = sDue.attempt();
        AttemptPart pStuck =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aStuck.getId()).get(2);
        AttemptPart pDue =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aDue.getId()).get(2);

        AtomicBoolean failDue = new AtomicBoolean(true);
        CountDownLatch stuckEntered = new CountDownLatch(1);
        CountDownLatch releaseStuck = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(pDue.getPartCode())) {
                                if (failDue.compareAndSet(true, false)) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "due-init-fail");
                                }
                                if (stuckEntered.getCount() > 0) {
                                    stuckEntered.countDown();
                                    assertThat(releaseStuck.await(15, TimeUnit.SECONDS)).isTrue();
                                }
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            assertThat(
                            performChoice(
                                            uDue,
                                            aDue.getId(),
                                            pDue.getId(),
                                            "swp-d-ch",
                                            sDue.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            awaitPartState(pDue.getId(), PartState.GENERATION_FAILED);

            assertThat(
                            performChoice(
                                            uStuck,
                                            aStuck.getId(),
                                            pStuck.getId(),
                                            "swp-s-ch",
                                            sStuck.sessionId(),
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(stuckEntered.await(10, TimeUnit.SECONDS)).isTrue();

            mutableClock.advance(Duration.ofMinutes(3));
            assertThat(worker.sweep()).isEqualTo(2);

            AttemptPart pStuckAfter = attemptPartRepository.findById(pStuck.getId()).orElseThrow();
            assertThat(pStuckAfter.getState()).isEqualTo(PartState.GENERATION_FAILED);
            assertThat(pStuckAfter.getGenerationError()).isEqualTo("STALE_GENERATING");
            assertThat(pStuckAfter.getGenerationTries()).isEqualTo(1);

            // Unblock the single executor thread (stale pStuck is discarded) so queued pDue runs & opens
            releaseStuck.countDown();
            awaitPartState(pDue.getId(), PartState.OPEN);
            CountDownLatch sentinel = new CountDownLatch(1);
            generationExecutor.submit(sentinel::countDown, sentinel::countDown);
            assertThat(sentinel.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(attemptPartRepository.findById(pStuck.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.GENERATION_FAILED);
        } finally {
            releaseStuck.countDown();
            reset(paperSolverPort);
        }

        // Recover pStuck cleanly before part 2
        mutableClock.advance(Duration.ofSeconds(35));
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(pStuck.getId(), PartState.OPEN);

        // 2. Per-part RuntimeException isolation: failure on candidate 1 does NOT abort candidate 2
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID isoU1 = createStudent("iso-1");
        UUID isoU2 = createStudent("iso-2");
        AttemptSession isoS1 =
                advanceToAwaitingChoice(
                        isoU1,
                        "iso1",
                        grant(isoU1, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        AttemptSession isoS2 =
                advanceToAwaitingChoice(
                        isoU2,
                        "iso2",
                        grant(isoU2, "DS-QDA-FULL-MINI", 2, List.of()).entitlementId());
        Attempt isoA1 = isoS1.attempt();
        Attempt isoA2 = isoS2.attempt();
        AttemptPart isoP1 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(isoA1.getId()).get(2);
        AttemptPart isoP2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(isoA2.getId()).get(2);

        AtomicBoolean failTwo = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if ((partCodes.contains(isoP1.getPartCode())
                                            || partCodes.contains(isoP2.getPartCode()))
                                    && failTwo.get()) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "iso-init");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        assertThat(
                        performChoice(
                                        isoU1,
                                        isoA1.getId(),
                                        isoP1.getId(),
                                        "iso1-ch",
                                        isoS1.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(isoP1.getId(), PartState.GENERATION_FAILED);
        assertThat(
                        performChoice(
                                        isoU2,
                                        isoA2.getId(),
                                        isoP2.getId(),
                                        "iso2-ch",
                                        isoS2.sessionId(),
                                        new ConfirmPartChoiceRequest("QD02", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(isoP2.getId(), PartState.GENERATION_FAILED);
        failTwo.set(false);

        mutableClock.advance(Duration.ofSeconds(35));
        doThrow(new RuntimeException("simulated loadVersion failure for isoA1"))
                .when(publishedExamPortSpy)
                .loadVersion(eq(isoA1.getExamId()), anyInt());
        assertThat(worker.retryFailedParts()).isEqualTo(1);
        awaitPartState(isoP2.getId(), PartState.OPEN);
        assertThat(attemptPartRepository.findById(isoP1.getId()).orElseThrow().getState())
                .isEqualTo(PartState.GENERATION_FAILED);

        // 3. FOR UPDATE SKIP LOCKED: holding FOR UPDATE on isoA1 does not block worker.sweep() on unlocked attempts
        reset(publishedExamPortSpy);
        UUID freeU = createStudent("skip-free");
        AttemptSession freeS =
                advanceToAwaitingChoice(
                        freeU,
                        "skfr",
                        grant(freeU, "DS-HSA-FULL-MINI", 2, List.of()).entitlementId());
        Attempt freeA = freeS.attempt();
        AttemptPart freeP =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(freeA.getId()).get(2);
        AtomicBoolean failFree = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(freeP.getPartCode())
                                    && failFree.compareAndSet(true, false)) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "free-init");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());
        assertThat(
                        performChoice(
                                        freeU,
                                        freeA.getId(),
                                        freeP.getId(),
                                        "skfr-ch",
                                        freeS.sessionId(),
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(freeP.getId(), PartState.GENERATION_FAILED);
        mutableClock.advance(Duration.ofSeconds(35));

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CompletableFuture<Void> holder =
                CompletableFuture.runAsync(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                s -> {
                                                    attemptRepository
                                                            .findByIdForUpdate(isoA1.getId())
                                                            .orElseThrow();
                                                    lockHeld.countDown();
                                                    try {
                                                        assertThat(
                                                                        releaseLock.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }));
        assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(worker.sweep()).isEqualTo(1);
            awaitPartState(freeP.getId(), PartState.OPEN);
            assertThat(attemptPartRepository.findById(isoP1.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.GENERATION_FAILED);
        } finally {
            releaseLock.countDown();
            holder.get(15, TimeUnit.SECONDS);
        }

        // After lock release, sweep picks up isoA1 cleanly
        assertThat(worker.sweep()).isEqualTo(1);
        awaitPartState(isoP1.getId(), PartState.OPEN);
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

    private AttemptSession advanceToAwaitingChoice(
            UUID userId, String keyPrefix, UUID entitlementId) throws Exception {
        Attempt attempt = startAndAwaitOpen(userId, keyPrefix, entitlementId, null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(1).getId(), sid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        return new AttemptSession(attempt, sid);
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
