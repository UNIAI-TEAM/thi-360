package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;

/**
 * Integration tests for idempotency replay/conflict/missing-key semantics on {@code choice} and
 * {@code retry}, and concurrent {@code choice} serialization (AC-16..AC-18).
 */
class AttemptChoiceIdempotencyIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void replayChoice() throws Exception {
        UUID userId = createStudent("idem-choice");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "ich", line.entitlementId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p2 = parts.get(2);

        // Missing and blank Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        MvcResult missing =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        null,
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missing).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        MvcResult blank =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "   ",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(blank).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        AtomicInteger p2Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                p2Calls.incrementAndGet();
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        ConfirmPartChoiceRequest req1 =
                new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SINH"));
        MvcResult first = performChoice(userId, attempt.getId(), p2.getId(), "ch-k1", null, req1);
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(json(first).path("state").asText()).isEqualTo("GENERATING");
        awaitPartState(p2.getId(), PartState.OPEN);
        assertThat(p2Calls.get()).isEqualTo(1);

        // Replay same key + same body -> 200 OK with CURRENT state (OPEN), zero extra generation
        MvcResult replay = performChoice(userId, attempt.getId(), p2.getId(), "ch-k1", null, req1);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode replayJson = json(replay);
        assertThat(replayJson.path("state").asText()).isEqualTo("OPEN");
        assertThat(replayJson.path("startedAt").isTextual()).isTrue();
        assertThat(p2Calls.get()).isEqualTo(1);

        // Replay same key + different body (different optionCode / different subjects / reordered subjects / different partId) -> 409 IDEMPOTENCY_KEY_REUSED
        for (ConfirmPartChoiceRequest diffBody :
                List.of(
                        new ConfirmPartChoiceRequest("ANH", null),
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU")),
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("SINH", "LY", "HOA")))) {
            MvcResult reused =
                    performChoice(userId, attempt.getId(), p2.getId(), "ch-k1", null, diffBody);
            assertThat(reused.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(reused).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }
        MvcResult diffPartReused =
                performChoice(userId, attempt.getId(), parts.get(0).getId(), "ch-k1", null, req1);
        assertThat(diffPartReused.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffPartReused).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // Different key + different choice -> 409 CHOICE_LOCKED
        MvcResult locked =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "ch-k2",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(locked.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(locked).path("code").asText()).isEqualTo("CHOICE_LOCKED");
    }

    @Test
    void replayRetry() throws Exception {
        UUID userId = createStudent("idem-retry");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "iret", line.entitlementId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p2 = parts.get(2);

        // Missing and blank Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        MvcResult missing = performRetry(userId, attempt.getId(), p2.getId(), null, null, null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missing).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        MvcResult blank = performRetry(userId, attempt.getId(), p2.getId(), "  ", null, null);
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(blank).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        AtomicInteger p2Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                int c = p2Calls.incrementAndGet();
                                if (c == 1) {
                                    throw new PaperGenerationException(
                                            PaperGenerationException.Reason.GENERATION_FAILED,
                                            "first try fails");
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
                                        "iret-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        RetryPartRequest retReq1 = new RetryPartRequest("retry 1");
        MvcResult firstRetry =
                performRetry(userId, attempt.getId(), p2.getId(), "ret-k1", null, retReq1);
        assertThat(firstRetry.getResponse().getStatus()).isEqualTo(202);
        assertThat(json(firstRetry).path("state").asText()).isEqualTo("GENERATING");
        awaitPartState(p2.getId(), PartState.OPEN);
        assertThat(p2Calls.get()).isEqualTo(2);

        // Replay same key + same body -> 200 OK with OPEN state, zero extra generation
        MvcResult replayRetry =
                performRetry(userId, attempt.getId(), p2.getId(), "ret-k1", null, retReq1);
        assertThat(replayRetry.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(replayRetry).path("state").asText()).isEqualTo("OPEN");
        assertThat(p2Calls.get()).isEqualTo(2);

        // Same key + different reason or different partId -> 409 IDEMPOTENCY_KEY_REUSED
        MvcResult diffReason =
                performRetry(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "ret-k1",
                        null,
                        new RetryPartRequest("retry 2"));
        assertThat(diffReason.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffReason).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        MvcResult diffPart =
                performRetry(userId, attempt.getId(), parts.get(0).getId(), "ret-k1", null, retReq1);
        assertThat(diffPart.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffPart).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void concurrentChoiceLocksOnce() throws Exception {
        // 1. Different-choice race -> one 202, one 409 CHOICE_LOCKED, 1 generation
        UUID u1 = createStudent("conc-diff");
        GrantOrderResult.Line l1 = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = advanceToAwaitingChoice(u1, "cdiff", l1.entitlementId());
        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);

        AtomicInteger p1Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p1.getPartCode())) {
                                p1Calls.incrementAndGet();
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier b1 = new CyclicBarrier(2);
            Future<MvcResult> f1 =
                    pool.submit(
                            () -> {
                                b1.await(10, TimeUnit.SECONDS);
                                return performChoice(
                                        u1,
                                        a1.getId(),
                                        p1.getId(),
                                        "cdiff-k1",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null));
                            });
            Future<MvcResult> f2 =
                    pool.submit(
                            () -> {
                                b1.await(10, TimeUnit.SECONDS);
                                return performChoice(
                                        u1,
                                        a1.getId(),
                                        p1.getId(),
                                        "cdiff-k2",
                                        null,
                                        new ConfirmPartChoiceRequest(
                                                "SCIENCE", List.of("LY", "HOA", "SU")));
                            });
            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);
            assertThat(List.of(r1.getResponse().getStatus(), r2.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(202, 409);
            MvcResult loser = r1.getResponse().getStatus() == 409 ? r1 : r2;
            assertThat(json(loser).path("code").asText()).isEqualTo("CHOICE_LOCKED");
            awaitPartState(p1.getId(), PartState.OPEN);
            assertThat(p1Calls.get()).isEqualTo(1);

            // 2. Identical-choice race with different keys -> one 202, one 200, strictly 1 generation
            reset(paperSolverPort);
            UUID u2 = createStudent("conc-same");
            GrantOrderResult.Line l2 = grant(u2, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt a2 = advanceToAwaitingChoice(u2, "csame", l2.entitlementId());
            AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);

            AtomicInteger p2Calls = new AtomicInteger();
            doAnswer(
                            inv -> {
                                List<String> partCodes = inv.getArgument(2);
                                if (partCodes.contains(p2.getPartCode())) {
                                    p2Calls.incrementAndGet();
                                }
                                return inv.callRealMethod();
                            })
                    .when(paperSolverPort)
                    .generate(any(), anyInt(), any(), any(), anyLong(), any());

            CyclicBarrier b2 = new CyclicBarrier(2);
            Future<MvcResult> s1 =
                    pool.submit(
                            () -> {
                                b2.await(10, TimeUnit.SECONDS);
                                return performChoice(
                                        u2,
                                        a2.getId(),
                                        p2.getId(),
                                        "csame-k1",
                                        null,
                                        new ConfirmPartChoiceRequest(
                                                "SCIENCE", List.of("LY", "HOA", "SINH")));
                            });
            Future<MvcResult> s2 =
                    pool.submit(
                            () -> {
                                b2.await(10, TimeUnit.SECONDS);
                                return performChoice(
                                        u2,
                                        a2.getId(),
                                        p2.getId(),
                                        "csame-k2",
                                        null,
                                        new ConfirmPartChoiceRequest(
                                                "science", List.of("sinh", "ly", "hoa")));
                            });
            MvcResult sr1 = s1.get(15, TimeUnit.SECONDS);
            MvcResult sr2 = s2.get(15, TimeUnit.SECONDS);
            assertThat(List.of(sr1.getResponse().getStatus(), sr2.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(202, 200);
            awaitPartState(p2.getId(), PartState.OPEN);
            assertThat(p2Calls.get()).isEqualTo(1);
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
