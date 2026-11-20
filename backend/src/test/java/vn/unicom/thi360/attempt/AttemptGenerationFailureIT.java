package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.application.AttemptStaleWorker;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Integration tests for async generation failures, busy queue drops, stale starting aborts, and
 * pre-save question deletion (AC-6..9, AC-30).
 */
@TestPropertySource(
        properties = {
            "thi360.attempt.generation.threads=1",
            "thi360.attempt.generation.queue-capacity=1",
            "thi360.attempt.generation.max-queue-wait=60s",
            "thi360.attempt.generation.stale-starting-after=2m"
        })
class AttemptGenerationFailureIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @Autowired private AttemptStaleWorker staleWorker;
    @Autowired private GenerationExecutor generationExecutor;

    @AfterEach
    void resetMocks() {
        reset(paperSolverPort);
    }

    @Test
    void solverErrorAbortsAndReleases() throws Exception {
        UUID userId = createStudent("solver-err");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        doThrow(new PaperGenerationException(PaperGenerationException.Reason.GENERATION_FAILED, "solver exploded"))
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult res = performStart(userId, "err-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.GENERATION_FAILED);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attemptId, "RESERVED")).isZero();

        var events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        assertThat(events).anySatisfy(ev -> {
            assertThat(ev.getType()).isEqualTo(AttemptEventType.GENERATION_FAILED);
            assertThat(ev.getData().get("examCode")).isEqualTo("DS-HSA-PART-MATH-MINI");
            assertThat(ev.getData().get("reason")).isEqualTo("GENERATION_FAILED");
            assertThat(ev.getData().get("versionNo")).isEqualTo(1);
            assertThat(ev.getData()).containsKey("path");
        });

        // Replay same Idempotency-Key after abort -> HTTP 200, ABORTED, GENERATION_FAILED, retryable=true, zero extra reserve (R1-39)
        MvcResult replay = performStart(userId, "err-1", line.entitlementId(), null);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        var replayJson = json(replay);
        assertThat(replayJson.path("attemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(replayJson.path("state").asText()).isEqualTo("ABORTED");
        assertThat(replayJson.path("resumed").asBoolean()).isFalse();
        assertThat(replayJson.path("abortReason").asText()).isEqualTo("GENERATION_FAILED");
        assertThat(replayJson.path("retryable").asBoolean()).isTrue();
        assertThat(countEntitlementUsageForGrant(line.grantId(), "RESERVED")).isZero();
    }

    @Test
    void solverTimeoutAbortsAndReleases() throws Exception {
        UUID userId = createStudent("solver-timeout");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        doThrow(new PaperGenerationException(PaperGenerationException.Reason.GENERATION_TIMEOUT, "budget exceeded"))
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult res = performStart(userId, "to-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.GENERATION_TIMEOUT);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
    }

    @Test
    void fullQueueAbortsWithBusy() throws Exception {
        UUID u1 = createStudent("busy-1");
        UUID u2 = createStudent("busy-2");
        UUID u3 = createStudent("busy-3");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-PART-MATH-MINI", 2, List.of());

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
            // Job 1 occupies the single worker thread
            MvcResult r1 = performStart(u1, "b-1", l1.entitlementId(), null);
            UUID id1 = UUID.fromString(json(r1).path("attemptId").asText());
            assertThat(blockerEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // Job 2 fills the single queue slot
            MvcResult r2 = performStart(u2, "b-2", l2.entitlementId(), null);
            UUID id2 = UUID.fromString(json(r2).path("attemptId").asText());

            // Job 3 overflows queue capacity 1 -> immediate GENERATION_BUSY abort (still returns 202 STARTING per R1-1/2)
            MvcResult r3 = performStart(u3, "b-3", l3.entitlementId(), null);
            assertThat(r3.getResponse().getStatus()).isEqualTo(202);
            assertThat(json(r3).path("state").asText()).isEqualTo("STARTING");
            assertThat(json(r3).path("resumed").asBoolean()).isFalse();
            UUID id3 = UUID.fromString(json(r3).path("attemptId").asText());

            Attempt aborted3 = awaitTerminalStartState(id3, AttemptState.ABORTED);
            assertThat(aborted3.getAbortReason()).isEqualTo(AbortReason.GENERATION_BUSY);
            assertThat(countEntitlementUsage(id3, "RELEASED")).isEqualTo(1);

            releaseBlocker.countDown();
            awaitTerminalStartState(id1, AttemptState.IN_PROGRESS);
            awaitTerminalStartState(id2, AttemptState.IN_PROGRESS);
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void queuedJobOlderThan60sIsDropped() throws Exception {
        // Case 1: queued job waits exact 60s -> still executes normally
        UUID u1 = createStudent("q60-1");
        UUID u2 = createStudent("q60-2");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line l2 = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());

        CountDownLatch b1Entered = new CountDownLatch(1);
        CountDownLatch b1Release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            b1Entered.countDown();
                            assertThat(b1Release.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult r1 = performStart(u1, "q60-k1", l1.entitlementId(), null);
        UUID id1 = UUID.fromString(json(r1).path("attemptId").asText());
        assertThat(b1Entered.await(10, TimeUnit.SECONDS)).isTrue();

        MvcResult r2 = performStart(u2, "q60-k2", l2.entitlementId(), null);
        UUID id2 = UUID.fromString(json(r2).path("attemptId").asText());

        mutableClock.advance(Duration.ofSeconds(60));
        reset(paperSolverPort);
        b1Release.countDown();

        awaitTerminalStartState(id1, AttemptState.IN_PROGRESS);
        awaitTerminalStartState(id2, AttemptState.IN_PROGRESS);

        // Case 2: queued job waits 61s (> 60s) -> dropped as GENERATION_BUSY and released
        UUID u3 = createStudent("q61-1");
        UUID u4 = createStudent("q61-2");
        GrantOrderResult.Line l3 = grant(u3, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line l4 = grant(u4, "DS-HSA-PART-MATH-MINI", 2, List.of());

        CountDownLatch b2Entered = new CountDownLatch(1);
        CountDownLatch b2Release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            b2Entered.countDown();
                            assertThat(b2Release.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            MvcResult r3 = performStart(u3, "q61-k1", l3.entitlementId(), null);
            UUID id3 = UUID.fromString(json(r3).path("attemptId").asText());
            assertThat(b2Entered.await(10, TimeUnit.SECONDS)).isTrue();

            MvcResult r4 = performStart(u4, "q61-k2", l4.entitlementId(), null);
            UUID id4 = UUID.fromString(json(r4).path("attemptId").asText());

            mutableClock.advance(Duration.ofSeconds(61));
            b2Release.countDown();

            awaitTerminalStartState(id3, AttemptState.IN_PROGRESS);
            Attempt dropped = awaitTerminalStartState(id4, AttemptState.ABORTED);
            assertThat(dropped.getAbortReason()).isEqualTo(AbortReason.GENERATION_BUSY);
            assertThat(countEntitlementUsage(id4, "RELEASED")).isEqualTo(1);
        } finally {
            b2Release.countDown();
        }
    }

    @Test
    void lateResultIsDiscarded() throws Exception {
        UUID userId = createStudent("late-discard");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        CountDownLatch solverEntered = new CountDownLatch(1);
        CountDownLatch solverRelease = new CountDownLatch(1);
        CountDownLatch sentinelDone = new CountDownLatch(1);
        AtomicBoolean sentinelBusy = new AtomicBoolean(false);

        doAnswer(
                        inv -> {
                            solverEntered.countDown();
                            assertThat(solverRelease.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            MvcResult res = performStart(userId, "late-1", line.entitlementId(), null);
            UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
            assertThat(solverEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // Advance past 2m stale threshold and run stale worker while solver is mid-flight
            mutableClock.advance(Duration.ofMinutes(3));
            assertThat(staleWorker.sweepStaleStarting()).isGreaterThanOrEqualTo(1);

            Attempt aborted = attemptRepository.findById(attemptId).orElseThrow();
            assertThat(aborted.getState()).isEqualTo(AttemptState.ABORTED);
            assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.STALE_STARTING);
            assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);

            // Queue sentinel behind the active 1-thread worker so barrier fires only after runGenerationJob finishes completely
            generationExecutor.submit(sentinelDone::countDown, () -> sentinelBusy.set(true));

            // Release solver late result; AttemptStartService must discard it silently
            solverRelease.countDown();
            assertThat(sentinelDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(sentinelBusy).isFalse();

            Attempt afterLate = attemptRepository.findById(attemptId).orElseThrow();
            assertThat(afterLate.getState()).isEqualTo(AttemptState.ABORTED);
            assertThat(afterLate.getAbortReason()).isEqualTo(AbortReason.STALE_STARTING);
            assertThat(afterLate.getAttemptNo()).isNull();
            assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
            assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
            assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        } finally {
            solverRelease.countDown();
        }
    }

    @Test
    void lateResultWithMismatchedTokenIsDiscarded() throws Exception {
        UUID userId = createStudent("late-token");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        CountDownLatch solverEntered = new CountDownLatch(1);
        CountDownLatch solverRelease = new CountDownLatch(1);
        CountDownLatch sentinelDone = new CountDownLatch(1);
        AtomicBoolean sentinelBusy = new AtomicBoolean(false);

        doAnswer(
                        inv -> {
                            solverEntered.countDown();
                            assertThat(solverRelease.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            MvcResult res = performStart(userId, "late-tok-1", line.entitlementId(), null);
            UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
            assertThat(solverEntered.await(10, TimeUnit.SECONDS)).isTrue();

            UUID newToken = Ids.newId();
            jdbc.update(
                    "UPDATE attempt.attempt SET generation_token = ? WHERE id = ?",
                    newToken,
                    attemptId);

            generationExecutor.submit(sentinelDone::countDown, () -> sentinelBusy.set(true));
            solverRelease.countDown();
            assertThat(sentinelDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(sentinelBusy).isFalse();

            Attempt afterLate = attemptRepository.findById(attemptId).orElseThrow();
            assertThat(afterLate.getState()).isEqualTo(AttemptState.STARTING);
            assertThat(afterLate.getGenerationToken()).isEqualTo(newToken);
            assertThat(afterLate.getAttemptNo()).isNull();
            assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
            assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
            assertThat(countEntitlementUsage(attemptId, "RESERVED")).isEqualTo(1);
            assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(0);
        } finally {
            solverRelease.countDown();
        }
    }

    @Test
    void missingRequestedPartAbortsWithGenerationFailed() throws Exception {
        UUID userId = createStudent("missing-part");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());

        doAnswer(
                        inv -> {
                            GeneratedPaper full = (GeneratedPaper) inv.callRealMethod();
                            assertThat(full.parts()).hasSizeGreaterThanOrEqualTo(2);
                            return new GeneratedPaper(List.of(full.parts().getFirst()), full.dropped());
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult res = performStart(userId, "miss-part-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.GENERATION_FAILED);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        var events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        assertThat(events).anyMatch(ev -> ev.getType() == AttemptEventType.GENERATION_FAILED);
    }

    @Test
    void questionDeletedBeforeSaveAborts() throws Exception {
        UUID userId = createStudent("del-before-save");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        AtomicReference<UUID> deletedQuestionId = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            GeneratedPaper paper = (GeneratedPaper) inv.callRealMethod();
                            UUID qId =
                                    paper.parts().getFirst().sections().getFirst().groups().getFirst()
                                            .questions().getFirst().questionId();
                            deletedQuestionId.set(qId);
                            jdbc.update(
                                    "UPDATE questionbank.question SET deleted_at = now() WHERE id = ?",
                                    qId);
                            return paper;
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            MvcResult res = performStart(userId, "del-save-1", line.entitlementId(), null);
            assertThat(res.getResponse().getStatus()).isEqualTo(202);
            UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

            Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
            assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.GENERATION_FAILED);
            assertThat(aborted.getAttemptNo()).isNull();
            assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
            assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
            assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        } finally {
            if (deletedQuestionId.get() != null) {
                jdbc.update(
                        "UPDATE questionbank.question SET deleted_at = NULL WHERE id = ?",
                        deletedQuestionId.get());
            }
        }
    }
}
