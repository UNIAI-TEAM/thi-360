package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.attempt.application.PartGenerationRetryWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Integration tests for Part 3 generation failures, non-expiry of failed parts, retry preserving
 * locked choice/seed/split, queue saturation {@code GENERATION_BUSY}, stale token discard, and
 * concurrent manual+worker retry deduplication (AC-9..AC-11, risk).
 */
@TestPropertySource(
        properties = {
            "thi360.attempt.generation.threads=1",
            "thi360.attempt.generation.queue-capacity=1",
            "thi360.attempt.generation.max-queue-wait=60s"
        })
class AttemptChoiceRetryIT extends AbstractAttemptDsIT {

    @Autowired private AttemptDeadlineWorker deadlineWorker;
    @Autowired private AttemptLifecycleService lifecycleService;
    @Autowired private PartGenerationRetryWorker retryWorker;
    @Autowired private GenerationExecutor generationExecutor;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void failedPartIsNotTimedOut() throws Exception {
        UUID userId = createStudent("fail-no-timeout");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "fnt", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "simulated part3 failure");
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
                                        "fnt-ch",
                                        null,
                                        new ConfirmPartChoiceRequest(
                                                "SCIENCE", List.of("LY", "HOA", "SU")))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);

        AttemptPart failed = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(failed.getGenerationTries()).isEqualTo(1);
        assertThat(failed.getGenerationError()).isEqualTo("GENERATION_FAILED");

        mutableClock.setAt(failed.getChoiceDeadlineAt().plus(Duration.ofMinutes(20)));
        assertThat(deadlineWorker.sweepOverdue()).isZero();
        MvcResult stateRes = performGetState(userId, attempt.getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(stateRes).path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(json(stateRes).path("parts").get(2).path("state").asText())
                .isEqualTo("GENERATION_FAILED");

        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);
    }

    @Test
    void unexpectedRuntimeExceptionWithNullMessageRecordsClassNameDetail() throws Exception {
        UUID userId = createStudent("npe-detail");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "npe", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        AtomicBoolean throwOnce = new AtomicBoolean(true);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())
                                    && throwOnce.compareAndSet(true, false)) {
                                throw new NullPointerException();
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
                                        "npe-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);

        AttemptPart failed = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(failed.getGenerationError()).isEqualTo("GENERATION_FAILED");
        assertThat(failed.getGenerationTries()).isEqualTo(1);

        List<AttemptEvent> failedEvents =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()).stream()
                        .filter(
                                e ->
                                        e.getType() == AttemptEventType.GENERATION_FAILED
                                                && p2.getId().equals(e.getPartId()))
                        .toList();
        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents.getFirst().getData())
                .containsEntry("reason", "GENERATION_FAILED")
                .containsEntry("detail", "NullPointerException");
    }

    @Test
    void retryKeepsChoiceSeedAndSplit() throws Exception {
        UUID userId = createStudent("retry-keeps");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "rk", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        AtomicInteger p2Calls = new AtomicInteger();
        AtomicLong initialSeed = new AtomicLong();
        AtomicLong retrySeed = new AtomicLong();
        AtomicReference<List<PaperSolverPort.Selection>> initialSelections = new AtomicReference<>();
        AtomicReference<List<PaperSolverPort.Selection>> retrySelections = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                int call = p2Calls.incrementAndGet();
                                @SuppressWarnings("unchecked")
                                List<PaperSolverPort.Selection> selections =
                                        List.copyOf(
                                                (List<PaperSolverPort.Selection>)
                                                        inv.getArgument(3));
                                long seedArg = inv.getArgument(4);
                                if (call == 1) {
                                    initialSeed.set(seedArg);
                                    initialSelections.set(selections);
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_TIMEOUT,
                                            "simulated timeout");
                                }
                                retrySeed.set(seedArg);
                                retrySelections.set(selections);
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        List<String> allNaturalTriple = List.of("LY", "HOA", "SINH");
        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "rk-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("SCIENCE", allNaturalTriple))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        AttemptPart failed = awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        assertThat(failed.getGenerationError()).isEqualTo("GENERATION_TIMEOUT");
        Map<String, Object> savedChoice = failed.getChoice();
        long savedSeed = failed.getSeed();

        int expectedLowIdx = new Random(savedSeed).nextInt(allNaturalTriple.size());
        Map<String, Integer> expectedSeedCounts = new LinkedHashMap<>();
        for (int i = 0; i < allNaturalTriple.size(); i++) {
            expectedSeedCounts.put(allNaturalTriple.get(i), i == expectedLowIdx ? 16 : 17);
        }
        @SuppressWarnings("unchecked")
        Map<String, Number> rawSavedCounts =
                (Map<String, Number>) savedChoice.get("subjectCounts");
        Map<String, Integer> savedCounts = new LinkedHashMap<>();
        rawSavedCounts.forEach((k, v) -> savedCounts.put(k, v.intValue()));
        assertThat(savedCounts).isEqualTo(expectedSeedCounts);

        MvcResult retryRes =
                performRetry(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "rk-retry-1",
                        null,
                        new RetryPartRequest("student retry"));
        assertThat(retryRes.getResponse().getStatus()).isEqualTo(202);
        assertThat(json(retryRes).path("state").asText()).isEqualTo("GENERATING");

        AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
        assertThat(p2Calls.get()).isEqualTo(2);
        assertThat(retrySeed.get()).isEqualTo(initialSeed.get()).isEqualTo(savedSeed);
        assertThat(retrySelections.get()).isEqualTo(initialSelections.get());
        assertThat(opened.getSeed()).isEqualTo(savedSeed);
        assertThat(opened.getChoice()).isEqualTo(savedChoice);
        assertThat(opened.getGenerationTries()).isZero();
        assertThat(opened.getGenerationError()).isNull();

        Map<String, Integer> unitsBySubject = new LinkedHashMap<>();
        for (AttemptItem item : attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId())) {
            if (item.getQuestionType() != QuestionType.QUESTION_SET) {
                unitsBySubject.merge(item.getComponentCode(), item.getUnitCount(), Integer::sum);
            }
        }
        assertThat(unitsBySubject).isEqualTo(savedCounts);
        assertThat(unitsBySubject.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(50);
    }

    @Test
    void fullQueueFailsPartNotAttempt() throws Exception {
        UUID u1 = createStudent("fq-b1");
        UUID u2 = createStudent("fq-b2");
        UUID u3 = createStudent("fq-choice");
        UUID u4 = createStudent("fq-retry");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line l4 = grant(u4, "DS-HSA-FULL-MINI", 2, List.of());

        Attempt a1 = advanceToAwaitingChoice(u1, "fq1", l1.entitlementId());
        Attempt a2 = advanceToAwaitingChoice(u2, "fq2", l2.entitlementId());
        Attempt a3 = advanceToAwaitingChoice(u3, "fq3", l3.entitlementId());
        Attempt a4 = advanceToAwaitingChoice(u4, "fq4", l4.entitlementId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        AttemptPart p3 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).get(2);
        AttemptPart p4 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a4.getId()).get(2);

        // First fail a4's Part 3 once so we can test retry under full queue too
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p4.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED, "pre-fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());
        assertThat(
                        performChoice(
                                        u4,
                                        a4.getId(),
                                        p4.getId(),
                                        "fq4-init",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p4.getId(), PartState.GENERATION_FAILED);

        reset(paperSolverPort);
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            blockerEntered.countDown();
                            assertThat(releaseBlocker.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            // Job 1 holds single worker thread
            assertThat(
                            performChoice(
                                            u1,
                                            a1.getId(),
                                            p1.getId(),
                                            "fq1-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(blockerEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // Job 2 occupies the 1-slot queue
            assertThat(
                            performChoice(
                                            u2,
                                            a2.getId(),
                                            p2.getId(),
                                            "fq2-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);

            // Job 3 via choice overflows -> GENERATION_BUSY on part, attempt stays IN_PROGRESS & RESERVED
            MvcResult c3 =
                    performChoice(
                            u3,
                            a3.getId(),
                            p3.getId(),
                            "fq3-ch",
                            null,
                            new ConfirmPartChoiceRequest("ANH", null));
            assertThat(c3.getResponse().getStatus()).isEqualTo(202);
            AttemptPart p3Failed = awaitPartState(p3.getId(), PartState.GENERATION_FAILED);
            assertThat(p3Failed.getGenerationError()).isEqualTo("GENERATION_BUSY");
            assertThat(p3Failed.getGenerationTries()).isEqualTo(1);
            assertThat(attemptRepository.findById(a3.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(countEntitlementUsage(a3.getId(), "RESERVED")).isEqualTo(1);

            // Job 4 via retry overflows -> GENERATION_BUSY on part, attempt stays IN_PROGRESS & RESERVED
            MvcResult r4 =
                    performRetry(
                            u4,
                            a4.getId(),
                            p4.getId(),
                            "fq4-ret",
                            null,
                            new RetryPartRequest("busy retry"));
            assertThat(r4.getResponse().getStatus()).isEqualTo(202);
            AttemptPart p4Failed = awaitPartState(p4.getId(), PartState.GENERATION_FAILED);
            assertThat(p4Failed.getGenerationError()).isEqualTo("GENERATION_BUSY");
            assertThat(p4Failed.getGenerationTries()).isEqualTo(2);
            assertThat(attemptRepository.findById(a4.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(countEntitlementUsage(a4.getId(), "RESERVED")).isEqualTo(1);

            releaseBlocker.countDown();
            awaitPartState(p1.getId(), PartState.OPEN);
            awaitPartState(p2.getId(), PartState.OPEN);
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void lateResultDiscardedByToken() throws Exception {
        // 1. Late success result from try 1 discarded when try 2 token replaced it; try 2 succeeds cleanly
        UUID u1 = createStudent("late-tok-ok");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = advanceToAwaitingChoice(u1, "lt1", l1.entitlementId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);

        CountDownLatch try1Entered = new CountDownLatch(1);
        CountDownLatch releaseTry1 = new CountDownLatch(1);
        CountDownLatch try2Entered = new CountDownLatch(1);
        CountDownLatch releaseTry2 = new CountDownLatch(1);
        AtomicInteger p1Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode())) {
                                int call = p1Calls.incrementAndGet();
                                if (call == 1) {
                                    try1Entered.countDown();
                                    assertThat(releaseTry1.await(15, TimeUnit.SECONDS)).isTrue();
                                } else if (call == 2) {
                                    try2Entered.countDown();
                                    assertThat(releaseTry2.await(15, TimeUnit.SECONDS)).isTrue();
                                }
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
                                            "lt1-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(try1Entered.await(10, TimeUnit.SECONDS)).isTrue();
            UUID try1Token =
                    attemptPartRepository.findById(p1.getId()).orElseThrow().getGenerationToken();

            // Mark stuck > 2m, then trigger manual retry (try 2 sits in 1-slot queue with a new token)
            mutableClock.advance(Duration.ofMinutes(3));
            assertThat(retryWorker.failStuckGenerating()).isEqualTo(1);
            assertThat(
                            performRetry(
                                            u1,
                                            a1.getId(),
                                            p1.getId(),
                                            "lt1-ret",
                                            null,
                                            new RetryPartRequest("try 2"))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            UUID try2Token =
                    attemptPartRepository.findById(p1.getId()).orElseThrow().getGenerationToken();
            assertThat(try2Token).isNotEqualTo(try1Token);

            // Release try 1 and wait for try 2 to enter solver (threads=1 proves try 1 exited persistGeneratedPart)
            releaseTry1.countDown();
            assertThat(try2Entered.await(10, TimeUnit.SECONDS)).isTrue();

            AttemptPart betweenTries = attemptPartRepository.findById(p1.getId()).orElseThrow();
            assertThat(betweenTries.getState()).isEqualTo(PartState.GENERATING);
            assertThat(betweenTries.getGenerationToken()).isEqualTo(try2Token);
            assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(p1.getId())).isEmpty();
            assertThat(
                            attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(a1.getId())
                                    .stream()
                                    .filter(
                                            e ->
                                                    e.getType() == AttemptEventType.PART_OPENED
                                                            && p1.getId().equals(e.getPartId())))
                    .isEmpty();

            // Advance clock before releasing try 2 so opened.startedAt strictly equals try2OpenedAt
            mutableClock.advance(Duration.ofSeconds(10));
            Instant try2OpenedAt = mutableClock.instant();
            releaseTry2.countDown();

            AttemptPart opened = awaitPartState(p1.getId(), PartState.OPEN);
            assertThat(opened.getStartedAt()).isEqualTo(try2OpenedAt);
            assertThat(opened.getGenerationTries()).isZero();
            List<AttemptItem> p1Items = attemptItemRepository.findByPartIdOrderByOrdAsc(p1.getId());
            int units =
                    p1Items.stream()
                            .filter(i -> i.getQuestionType() != QuestionType.QUESTION_SET)
                            .mapToInt(AttemptItem::getUnitCount)
                            .sum();
            assertThat(units).isEqualTo(p1.getQuestionCountRequired());
            assertThat(p1Calls.get()).isEqualTo(2);
            assertThat(
                            attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(a1.getId())
                                    .stream()
                                    .filter(
                                            e ->
                                                    e.getType() == AttemptEventType.PART_OPENED
                                                            && p1.getId().equals(e.getPartId())))
                    .hasSize(1);
        } finally {
            releaseTry1.countDown();
            releaseTry2.countDown();
        }

        // 2. Late error from stale try 1 does NOT clobber try 2
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        reset(paperSolverPort);
        UUID u2 = createStudent("late-tok-err");
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a2 = advanceToAwaitingChoice(u2, "lt2", l2.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);

        CountDownLatch errTry1Entered = new CountDownLatch(1);
        CountDownLatch releaseErrTry1 = new CountDownLatch(1);
        CountDownLatch errTry2Entered = new CountDownLatch(1);
        CountDownLatch releaseErrTry2 = new CountDownLatch(1);
        AtomicInteger p2Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                int call = p2Calls.incrementAndGet();
                                if (call == 1) {
                                    errTry1Entered.countDown();
                                    assertThat(releaseErrTry1.await(15, TimeUnit.SECONDS)).isTrue();
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "stale try 1 failure");
                                } else if (call == 2) {
                                    errTry2Entered.countDown();
                                    assertThat(releaseErrTry2.await(15, TimeUnit.SECONDS)).isTrue();
                                }
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            assertThat(
                            performChoice(
                                            u2,
                                            a2.getId(),
                                            p2.getId(),
                                            "lt2-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(errTry1Entered.await(10, TimeUnit.SECONDS)).isTrue();

            mutableClock.advance(Duration.ofMinutes(3));
            assertThat(retryWorker.failStuckGenerating()).isEqualTo(1);
            assertThat(
                            performRetry(
                                            u2,
                                            a2.getId(),
                                            p2.getId(),
                                            "lt2-ret",
                                            null,
                                            new RetryPartRequest("try 2"))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            UUID errTry2Token =
                    attemptPartRepository.findById(p2.getId()).orElseThrow().getGenerationToken();

            releaseErrTry1.countDown();
            assertThat(errTry2Entered.await(10, TimeUnit.SECONDS)).isTrue();
            AttemptPart p2WhileTry2 = attemptPartRepository.findById(p2.getId()).orElseThrow();
            assertThat(p2WhileTry2.getState()).isEqualTo(PartState.GENERATING);
            assertThat(p2WhileTry2.getGenerationToken()).isEqualTo(errTry2Token);
            assertThat(p2WhileTry2.getGenerationTries()).isEqualTo(1);
            assertThat(p2WhileTry2.getGenerationError()).isNull();

            releaseErrTry2.countDown();
            AttemptPart opened2 = awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(opened2.getGenerationTries()).isZero();
            assertThat(opened2.getGenerationError()).isNull();
            assertThat(p2Calls.get()).isEqualTo(2);
        } finally {
            releaseErrTry1.countDown();
            releaseErrTry2.countDown();
        }
    }

    @Test
    void midGenerationCloseDiscardsItemsAndUsage() throws Exception {
        UUID userId = createStudent("mid-gen-close");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "mgc", line.entitlementId());
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
                                            "mgc-ch",
                                            null,
                                            new ConfirmPartChoiceRequest("ANH", null))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            Integer ledgerBefore =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM questionbank.question_usage_ledger
                            WHERE exam_id = ? AND kind = 'ATTEMPT'
                            """,
                            Integer.class,
                            attempt.getExamId());
            assertThat(ledgerBefore).isNotNull();

            lifecycleService.closeAttempt(
                    attempt.getId(), EndReason.FAULT_CLOSED, mutableClock.instant());
            assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.GRADING);
            AttemptPart skipped = attemptPartRepository.findById(p2.getId()).orElseThrow();
            assertThat(skipped.getState()).isEqualTo(PartState.SKIPPED);
            assertThat(skipped.getCloseReason()).isEqualTo(PartCloseReason.FAULT_CLOSED);

            release.countDown();
            CountDownLatch sentinel = new CountDownLatch(1);
            generationExecutor.submit(sentinel::countDown, sentinel::countDown);
            assertThat(sentinel.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId())).isEmpty();
            Integer ledgerAfter =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM questionbank.question_usage_ledger
                            WHERE exam_id = ? AND kind = 'ATTEMPT'
                            """,
                            Integer.class,
                            attempt.getExamId());
            assertThat(ledgerAfter).isEqualTo(ledgerBefore);
            assertThat(attemptPartRepository.findById(p2.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.SKIPPED);
            assertThat(
                            attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId())
                                    .stream()
                                    .filter(
                                            e ->
                                                    e.getType() == AttemptEventType.PART_OPENED
                                                            && p2.getId().equals(e.getPartId())))
                    .isEmpty();
        } finally {
            release.countDown();
        }
    }

    @Test
    void concurrentManualAndWorkerRetryRunsOnce() throws Exception {
        UUID userId = createStudent("conc-manual-worker");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "cmw", line.entitlementId());
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        java.util.concurrent.atomic.AtomicBoolean shouldFailInitial =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        AtomicInteger p2Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                p2Calls.incrementAndGet();
                                if (shouldFailInitial.compareAndSet(true, false)) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "initial failure");
                                }
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
                                        "cmw-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        mutableClock.advance(Duration.ofSeconds(35));
        p2Calls.set(0);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> manualFuture =
                    pool.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return performRetry(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "cmw-manual",
                                        null,
                                        new RetryPartRequest("race"));
                            });
            Future<Integer> workerFuture =
                    pool.submit(
                            () -> {
                                barrier.await(10, TimeUnit.SECONDS);
                                return retryWorker.retryFailedParts();
                            });

            MvcResult manualRes = manualFuture.get(15, TimeUnit.SECONDS);
            int workerCount = workerFuture.get(15, TimeUnit.SECONDS);

            AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(opened.getGenerationTries()).isZero();
            // Flush executor queue to ensure no stray second generation runs after OPEN
            CountDownLatch sentinel = new CountDownLatch(1);
            generationExecutor.submit(sentinel::countDown, sentinel::countDown);
            assertThat(sentinel.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(p2Calls.get()).isEqualTo(1);
            if (workerCount == 1) {
                assertThat(manualRes.getResponse().getStatus()).isEqualTo(409);
                assertThat(json(manualRes).path("code").asText())
                        .isIn("PART_GENERATING", "PART_NOT_AWAITING");
            } else {
                assertThat(manualRes.getResponse().getStatus()).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
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
