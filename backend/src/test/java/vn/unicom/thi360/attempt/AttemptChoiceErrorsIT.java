package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for all 6 LT §6.1 choice/retry error codes, {@code X-Attempt-Session} forwarding
 * and revocation (including idempotent replays), unpublished path guards, {@link
 * ForbiddenKeysAssert} verification, and ownership/role access checks (AC-15, AC-20, AC-21).
 */
class AttemptChoiceErrorsIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private AttemptSessionChecker attemptSessionChecker;
    @MockitoSpyBean private PublishedExamPort publishedExamPortSpy;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort, attemptSessionChecker, publishedExamPortSpy);
    }

    @Test
    void choiceAndRetryErrorCodesMatchSpec() throws Exception {
        UUID userId = createStudent("err-codes");
        GrantOrderResult.Line hsaLine = grant(userId, "DS-HSA-FULL-MINI", 4, List.of());

        // 1. PART_NOT_AWAITING when calling choice or retry on OPEN Part 0 or PENDING Part 2
        Attempt a1 = startAndAwaitOpen(userId, "ec-1", hsaLine.entitlementId(), null);
        UUID sid1 = claimSession(userId, a1.getId());
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        MvcResult choiceP0 =
                performChoice(
                        userId,
                        a1.getId(),
                        a1Parts.get(0).getId(),
                        "ec-p0",
                        sid1,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(choiceP0.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(choiceP0).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        MvcResult choicePendingP2 =
                performChoice(
                        userId,
                        a1.getId(),
                        a1Parts.get(2).getId(),
                        "ec-p2-pend",
                        sid1,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(choicePendingP2.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(choicePendingP2).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        // Advance a1 to Part 2 AWAITING_CHOICE
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(0).getId(), sid1, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(1).getId(), sid1, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a1P2 = attemptPartRepository.findById(a1Parts.get(2).getId()).orElseThrow();

        // retry on AWAITING_CHOICE -> 409 PART_NOT_AWAITING
        MvcResult retryAwaiting =
                performRetry(userId, a1.getId(), a1P2.getId(), "ec-ret-await", sid1, null);
        assertThat(retryAwaiting.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(retryAwaiting).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        // 2. CHOICE_NOT_ALLOWED: null/blank/unknown optionCode -> 422
        for (ConfirmPartChoiceRequest badOpt :
                List.of(
                        new ConfirmPartChoiceRequest(null, null),
                        new ConfirmPartChoiceRequest("   ", null),
                        new ConfirmPartChoiceRequest("NOPE", null))) {
            MvcResult r =
                    performChoice(
                            userId,
                            a1.getId(),
                            a1P2.getId(),
                            "ec-badopt-" + UUID.randomUUID(),
                            sid1,
                            badOpt);
            assertThat(r.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(r).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");
        }

        // 3. SUBJECTS_INVALID: wrong count (2 or 4), duplicate, unknown, or subjects on ANH -> 422
        for (ConfirmPartChoiceRequest badSubj :
                List.of(
                        new ConfirmPartChoiceRequest("SCIENCE", null),
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA")),
                        new ConfirmPartChoiceRequest(
                                "SCIENCE", List.of("LY", "HOA", "SINH", "SU")),
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "ly", "HOA")),
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "ART")),
                        new ConfirmPartChoiceRequest("ANH", List.of("LY")))) {
            MvcResult r =
                    performChoice(
                            userId,
                            a1.getId(),
                            a1P2.getId(),
                            "ec-badsubj-" + UUID.randomUUID(),
                            sid1,
                            badSubj);
            assertThat(r.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(r).path("code").asText()).isEqualTo("SUBJECTS_INVALID");
        }

        // 4. PART_GENERATING on retry while Part 3 is GENERATING + CHOICE_LOCKED on different choice
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(a1P2.getPartCode())) {
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
                                            a1.getId(),
                                            a1P2.getId(),
                                            "ec-ok-ch",
                                            sid1,
                                            new ConfirmPartChoiceRequest(
                                                    "SCIENCE", List.of("LY", "HOA", "SU")))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            MvcResult retryGenerating =
                    performRetry(userId, a1.getId(), a1P2.getId(), "ec-ret-gen", sid1, null);
            assertThat(retryGenerating.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(retryGenerating).path("code").asText()).isEqualTo("PART_GENERATING");

            MvcResult diffWhileGen =
                    performChoice(
                            userId,
                            a1.getId(),
                            a1P2.getId(),
                            "ec-diff-gen",
                            sid1,
                            new ConfirmPartChoiceRequest("ANH", null));
            assertThat(diffWhileGen.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(diffWhileGen).path("code").asText()).isEqualTo("CHOICE_LOCKED");

            release.countDown();
            awaitPartState(a1P2.getId(), PartState.OPEN);
        } finally {
            release.countDown();
        }

        // CHOICE_LOCKED after OPEN too (both different optionCode and different subjectCodes)
        MvcResult diffOptAfterOpen =
                performChoice(
                        userId,
                        a1.getId(),
                        a1P2.getId(),
                        "ec-diff-open-1",
                        sid1,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(diffOptAfterOpen.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffOptAfterOpen).path("code").asText()).isEqualTo("CHOICE_LOCKED");

        MvcResult diffSubjAfterOpen =
                performChoice(
                        userId,
                        a1.getId(),
                        a1P2.getId(),
                        "ec-diff-open-2",
                        sid1,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "DIA")));
        assertThat(diffSubjAfterOpen.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffSubjAfterOpen).path("code").asText()).isEqualTo("CHOICE_LOCKED");

        // Close a1 so we can start a2, then verify post-submit choice (same & different) and retry -> 409 PART_NOT_AWAITING
        assertThat(performSubmit(userId, a1.getId(), "ec-sub-a1", sid1, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        MvcResult postSubmitSame =
                performChoice(
                        userId,
                        a1.getId(),
                        a1P2.getId(),
                        "ec-post-sub-same",
                        sid1,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU")));
        assertThat(postSubmitSame.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(postSubmitSame).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        MvcResult postSubmitDiff =
                performChoice(
                        userId,
                        a1.getId(),
                        a1P2.getId(),
                        "ec-post-sub-diff",
                        sid1,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(postSubmitDiff.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(postSubmitDiff).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        MvcResult postSubmitRetry =
                performRetry(userId, a1.getId(), a1P2.getId(), "ec-post-sub-ret", sid1, null);
        assertThat(postSubmitRetry.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(postSubmitRetry).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        // 5. CHOICE_WINDOW_CLOSED when past 15-minute window
        Attempt a2 = startAndAwaitOpen(userId, "ec-2", hsaLine.entitlementId(), null);
        UUID sid2 = claimSession(userId, a2.getId());
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(0).getId(), sid2, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(1).getId(), sid2, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a2P2 = attemptPartRepository.findById(a2Parts.get(2).getId()).orElseThrow();
        mutableClock.setAt(a2P2.getChoiceDeadlineAt().plusSeconds(1));

        MvcResult windowClosed =
                performChoice(
                        userId,
                        a2.getId(),
                        a2P2.getId(),
                        "ec-win-closed",
                        sid2,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(windowClosed.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(windowClosed).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

        // Calling choice again when already SKIPPED(CHOICE_TIMEOUT) still yields CHOICE_WINDOW_CLOSED
        MvcResult windowClosedAgain =
                performChoice(
                        userId,
                        a2.getId(),
                        a2P2.getId(),
                        "ec-win-closed-2",
                        sid2,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(windowClosedAgain.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(windowClosedAgain).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

        // 6. Bean Validation: subjectCodes size > 10 & retry reason > 200 -> 400 VALIDATION_FAILED
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt a3 = startAndAwaitOpen(userId, "ec-3", hsaLine.entitlementId(), null);
        UUID sid3 = claimSession(userId, a3.getId());
        List<AttemptPart> a3Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId());
        assertThat(performFinishPart(userId, a3.getId(), a3Parts.get(0).getId(), sid3, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a3.getId(), a3Parts.get(1).getId(), sid3, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        MvcResult tooManySubj =
                performChoice(
                        userId,
                        a3.getId(),
                        a3Parts.get(2).getId(),
                        "ec-val-1",
                        sid3,
                        new ConfirmPartChoiceRequest(
                                "SCIENCE",
                                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")));
        assertThat(tooManySubj.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(tooManySubj).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        MvcResult longReason =
                performRetry(
                        userId,
                        a3.getId(),
                        a3Parts.get(2).getId(),
                        "ec-val-2",
                        sid3,
                        new RetryPartRequest("x".repeat(201)));
        assertThat(longReason.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longReason).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        MvcResult longOptionCode =
                performChoice(
                        userId,
                        a3.getId(),
                        a3Parts.get(2).getId(),
                        "ec-val-3",
                        sid3,
                        new ConfirmPartChoiceRequest("A".repeat(65), null));
        assertThat(longOptionCode.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longOptionCode).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        MvcResult longSubjectCode =
                performChoice(
                        userId,
                        a3.getId(),
                        a3Parts.get(2).getId(),
                        "ec-val-4",
                        sid3,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("S".repeat(65))));
        assertThat(longSubjectCode.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longSubjectCode).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        // 7. Unpublished option on HSA -> 422 CHOICE_NOT_ALLOWED (isTokenOnPublishedPath == false)
        AttemptPart a3P2 = a3Parts.get(2);
        PublishedVersion realHsaVer =
                publishedExamPort.loadVersion(a3.getExamId(), a3.getExamVersionNo()).orElseThrow();
        Part realHsaPart3 =
                realHsaVer.parts().stream()
                        .filter(p -> p.id().equals(a3P2.getPartCode()))
                        .findFirst()
                        .orElseThrow();
        String anhOptId =
                realHsaPart3.options().stream()
                        .filter(o -> "ANH".equals(o.code()))
                        .map(Option::id)
                        .findFirst()
                        .orElseThrow();
        List<String> hsaPathsWithoutAnh =
                realHsaVer.publishedPathIds().stream()
                        .filter(pid -> !pid.contains(anhOptId))
                        .toList();
        PublishedVersion unpubHsaVer =
                new PublishedVersion(
                        realHsaVer.examId(),
                        realHsaVer.versionNo(),
                        realHsaVer.state(),
                        realHsaVer.status(),
                        realHsaVer.publishedAt(),
                        realHsaVer.code(),
                        realHsaVer.name(),
                        realHsaVer.kindItemId(),
                        realHsaVer.kindCode(),
                        realHsaVer.scope(),
                        realHsaVer.partCode(),
                        realHsaVer.subjectItemId(),
                        realHsaVer.buildMode(),
                        realHsaVer.timing(),
                        realHsaVer.durationMinutes(),
                        realHsaVer.maxScore(),
                        realHsaVer.referenceMax(),
                        realHsaVer.practiceScoring(),
                        realHsaVer.listing(),
                        realHsaVer.parts(),
                        realHsaVer.combinations(),
                        realHsaVer.omittedGroups(),
                        hsaPathsWithoutAnh,
                        realHsaVer.servingPathIds(),
                        realHsaVer.instructions());
        doAnswer(inv -> Optional.of(unpubHsaVer))
                .when(publishedExamPortSpy)
                .loadVersion(a3.getExamId(), a3.getExamVersionNo());

        MvcResult unpubHsaRes =
                performChoice(
                        userId,
                        a3.getId(),
                        a3P2.getId(),
                        "ec-unpub-hsa",
                        sid3,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(unpubHsaRes.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(unpubHsaRes).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");

        reset(publishedExamPortSpy);
        assertThat(performSubmit(userId, a3.getId(), "ec-sub-a3", sid3, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(409); // sequential Part 2 unfinished; close cleanly for QDA below
        jdbc.update("UPDATE attempt.attempt SET state = 'SCORED' WHERE id = ?", a3.getId());

        // 8. QDA QD02 with unexpected subjectCodes=["HOA"] -> 422 SUBJECTS_INVALID, and unpublished combination -> 422 CHOICE_NOT_ALLOWED
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        GrantOrderResult.Line qdaLine = grant(userId, "DS-QDA-FULL-MINI", 2, List.of());
        Attempt qdaAttempt = startAndAwaitOpen(userId, "ec-qda", qdaLine.entitlementId(), null);
        UUID qdaSid = claimSession(userId, qdaAttempt.getId());
        List<AttemptPart> qdaParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(qdaAttempt.getId());
        assertThat(performFinishPart(userId, qdaAttempt.getId(), qdaParts.get(0).getId(), qdaSid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, qdaAttempt.getId(), qdaParts.get(1).getId(), qdaSid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart qdaP2 = qdaParts.get(2);

        MvcResult qdaWithSubj =
                performChoice(
                        userId,
                        qdaAttempt.getId(),
                        qdaP2.getId(),
                        "ec-qda-subj",
                        qdaSid,
                        new ConfirmPartChoiceRequest("QD02", List.of("HOA")));
        assertThat(qdaWithSubj.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(qdaWithSubj).path("code").asText()).isEqualTo("SUBJECTS_INVALID");

        PublishedVersion realQdaVer =
                publishedExamPort
                        .loadVersion(qdaAttempt.getExamId(), qdaAttempt.getExamVersionNo())
                        .orElseThrow();
        String qd02Token = qdaP2.getPartCode() + ":QD02";
        List<String> qdaPathsWithoutQd02 =
                realQdaVer.publishedPathIds().stream()
                        .filter(pid -> !pid.contains(qd02Token))
                        .toList();
        PublishedVersion unpubQdaVer =
                new PublishedVersion(
                        realQdaVer.examId(),
                        realQdaVer.versionNo(),
                        realQdaVer.state(),
                        realQdaVer.status(),
                        realQdaVer.publishedAt(),
                        realQdaVer.code(),
                        realQdaVer.name(),
                        realQdaVer.kindItemId(),
                        realQdaVer.kindCode(),
                        realQdaVer.scope(),
                        realQdaVer.partCode(),
                        realQdaVer.subjectItemId(),
                        realQdaVer.buildMode(),
                        realQdaVer.timing(),
                        realQdaVer.durationMinutes(),
                        realQdaVer.maxScore(),
                        realQdaVer.referenceMax(),
                        realQdaVer.practiceScoring(),
                        realQdaVer.listing(),
                        realQdaVer.parts(),
                        realQdaVer.combinations(),
                        realQdaVer.omittedGroups(),
                        qdaPathsWithoutQd02,
                        realQdaVer.servingPathIds(),
                        realQdaVer.instructions());
        doAnswer(inv -> Optional.of(unpubQdaVer))
                .when(publishedExamPortSpy)
                .loadVersion(qdaAttempt.getExamId(), qdaAttempt.getExamVersionNo());

        MvcResult unpubQdaRes =
                performChoice(
                        userId,
                        qdaAttempt.getId(),
                        qdaP2.getId(),
                        "ec-unpub-qda",
                        qdaSid,
                        new ConfirmPartChoiceRequest("QD02", null));
        assertThat(unpubQdaRes.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(unpubQdaRes).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");
    }

    @Test
    void forwardsAttemptSessionIdAndRejectsRevokedSession() throws Exception {
        UUID userId = createStudent("choice-session");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());
        Attempt a1 = startAndAwaitOpen(userId, "sess-a1", line.entitlementId(), null);
        UUID activeSessionId = UUID.randomUUID();
        assertThat(
                        performClaimSession(
                                        userId,
                                        a1.getId(),
                                        new ClaimSessionRequest(activeSessionId, false))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(0).getId(), activeSessionId, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(1).getId(), activeSessionId, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a1P2 = a1Parts.get(2);

        AtomicInteger calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(a1P2.getPartCode())
                                    && calls.incrementAndGet() == 1) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "fail-once");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        clearInvocations(attemptSessionChecker);

        ConfirmPartChoiceRequest choiceReq = new ConfirmPartChoiceRequest("ANH", null);
        assertThat(
                        performChoice(
                                        userId,
                                        a1.getId(),
                                        a1P2.getId(),
                                        "sess-ch-1",
                                        activeSessionId,
                                        choiceReq)
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(a1P2.getId(), PartState.GENERATION_FAILED);

        // Idempotent replay with active session -> 200 OK
        assertThat(
                        performChoice(
                                        userId,
                                        a1.getId(),
                                        a1P2.getId(),
                                        "sess-ch-1",
                                        activeSessionId,
                                        choiceReq)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        RetryPartRequest retryReq = new RetryPartRequest("active sess");
        assertThat(
                        performRetry(
                                        userId,
                                        a1.getId(),
                                        a1P2.getId(),
                                        "sess-ret-1",
                                        activeSessionId,
                                        retryReq)
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(a1P2.getId(), PartState.OPEN);

        // Idempotent replay of retry with active session -> 200 OK
        assertThat(
                        performRetry(
                                        userId,
                                        a1.getId(),
                                        a1P2.getId(),
                                        "sess-ret-1",
                                        activeSessionId,
                                        retryReq)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        verify(attemptSessionChecker, times(4))
                .requireEditSession(argThat(a -> a.getId().equals(a1.getId())), eq(activeSessionId));

        List<AttemptEvent> p2Events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(a1.getId()).stream()
                        .filter(e -> a1P2.getId().equals(e.getPartId()))
                        .toList();
        assertThat(p2Events)
                .filteredOn(e -> e.getType() == AttemptEventType.CHOICE_CONFIRMED)
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));
        assertThat(p2Events)
                .filteredOn(e -> e.getType() == AttemptEventType.GENERATION_FAILED)
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));
        assertThat(p2Events)
                .filteredOn(e -> e.getType() == AttemptEventType.PART_OPENED)
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));

        // Revoked session on idempotent replays while IN_PROGRESS -> 409 ATTEMPT_SESSION_REVOKED
        UUID revokedSessionId = UUID.randomUUID();

        MvcResult replayChoiceRev =
                performChoice(
                        userId, a1.getId(), a1P2.getId(), "sess-ch-1", revokedSessionId, choiceReq);
        assertThat(replayChoiceRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(replayChoiceRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

        MvcResult replayRetryRev =
                performRetry(
                        userId, a1.getId(), a1P2.getId(), "sess-ret-1", revokedSessionId, retryReq);
        assertThat(replayRetryRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(replayRetryRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

        // Close a1, then verify post-close idempotent replays with revoked session return 200 OK with GRADING
        assertThat(performSubmit(userId, a1.getId(), "sess-sub-a1", activeSessionId, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        MvcResult postCloseChoice =
                performChoice(
                        userId, a1.getId(), a1P2.getId(), "sess-ch-1", revokedSessionId, choiceReq);
        assertThat(postCloseChoice.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(postCloseChoice).path("attemptState").asText()).isEqualTo("GRADING");

        MvcResult postCloseRetry =
                performRetry(
                        userId, a1.getId(), a1P2.getId(), "sess-ret-1", revokedSessionId, retryReq);
        assertThat(postCloseRetry.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(postCloseRetry).path("attemptState").asText()).isEqualTo("GRADING");

        // Test revoked session on initial choice and initial retry on a2 without state mutation

        Attempt a2 = startAndAwaitOpen(userId, "sess-a2", line.entitlementId(), null);
        UUID a2Sid = claimSession(userId, a2.getId());
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(0).getId(), a2Sid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(1).getId(), a2Sid, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a2P2 = a2Parts.get(2);

        MvcResult initChoiceRev =
                performChoice(
                        userId,
                        a2.getId(),
                        a2P2.getId(),
                        "sess-a2-ch-rev",
                        revokedSessionId,
                        choiceReq);
        assertThat(initChoiceRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(initChoiceRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptPartRepository.findById(a2P2.getId()).orElseThrow().getState())
                .isEqualTo(PartState.AWAITING_CHOICE);

        // Fail a2P2 with valid session, then verify initial retry with revoked session -> 409 ATTEMPT_SESSION_REVOKED
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(a2P2.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "fail-a2");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());
        assertThat(
                        performChoice(
                                        userId,
                                        a2.getId(),
                                        a2P2.getId(),
                                        "sess-a2-ch-ok",
                                        a2Sid,
                                        choiceReq)
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(a2P2.getId(), PartState.GENERATION_FAILED);

        MvcResult initRetryRev =
                performRetry(
                        userId,
                        a2.getId(),
                        a2P2.getId(),
                        "sess-a2-ret-rev",
                        revokedSessionId,
                        retryReq);
        assertThat(initRetryRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(initRetryRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptPartRepository.findById(a2P2.getId()).orElseThrow().getState())
                .isEqualTo(PartState.GENERATION_FAILED);
    }

    @Test
    void forbiddenKeysAbsent() throws Exception {
        UUID userId = createStudent("choice-forbid");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "cf", line.entitlementId(), null);
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
        AttemptPart p2 = parts.get(2);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                performGetState(userId, attempt.getId()).getResponse().getContentAsString());

        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED, "fail");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult choice202 =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "cf-ch",
                        sid,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU")));
        assertThat(choice202.getResponse().getStatus()).isEqualTo(202);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                choice202.getResponse().getContentAsString());

        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                performGetState(userId, attempt.getId()).getResponse().getContentAsString());

        reset(paperSolverPort);
        MvcResult retry202 =
                performRetry(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "cf-ret",
                        sid,
                        new RetryPartRequest("retry"));
        assertThat(retry202.getResponse().getStatus()).isEqualTo(202);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                retry202.getResponse().getContentAsString());

        awaitPartState(p2.getId(), PartState.OPEN);
        MvcResult choiceReplay =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "cf-ch",
                        sid,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU")));
        assertThat(choiceReplay.getResponse().getStatus()).isEqualTo(200);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                choiceReplay.getResponse().getContentAsString());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                performGetState(userId, attempt.getId()).getResponse().getContentAsString());
    }

    @Test
    void ownershipAndAccess() throws Exception {
        UUID owner = createStudent("ch-owner");
        UUID stranger = createStudent("ch-stranger");
        UUID manager = createManager("ch-manager");

        GrantOrderResult.Line ownerLine = grant(owner, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt ownerAttempt = startAndAwaitOpen(owner, "own-1", ownerLine.entitlementId(), null);
        UUID ownerSid = claimSession(owner, ownerAttempt.getId());
        List<AttemptPart> ownerParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(ownerAttempt.getId());
        assertThat(
                        performFinishPart(
                                        owner,
                                        ownerAttempt.getId(),
                                        ownerParts.get(0).getId(),
                                        ownerSid,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performFinishPart(
                                        owner,
                                        ownerAttempt.getId(),
                                        ownerParts.get(1).getId(),
                                        ownerSid,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        UUID p2Id = ownerParts.get(2).getId();

        GrantOrderResult.Line strangerLine = grant(stranger, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt strangerAttempt =
                startAndAwaitOpen(stranger, "str-1", strangerLine.entitlementId(), null);
        UUID foreignPartId =
                attemptPartRepository
                        .findByAttemptIdOrderByOrdAsc(strangerAttempt.getId())
                        .getFirst()
                        .getId();

        String choiceUrl =
                "/api/v1/me/attempts/" + ownerAttempt.getId() + "/parts/" + p2Id + "/choice";
        String retryUrl =
                "/api/v1/me/attempts/" + ownerAttempt.getId() + "/parts/" + p2Id + "/retry";

        // 1. Anonymous -> 401
        assertThat(
                        mockMvc.perform(
                                        post(choiceUrl)
                                                .header("Idempotency-Key", "anon-c")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"optionCode\":\"ANH\"}"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
        assertThat(
                        mockMvc.perform(post(retryUrl).header("Idempotency-Key", "anon-r"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);

        // 2. MANAGE -> 403 ATTEMPT_ACCESS_FORBIDDEN
        MvcResult mgrChoice =
                mockMvc.perform(
                                post(choiceUrl)
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr"))
                                        .header("Idempotency-Key", "mgr-c")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"optionCode\":\"ANH\"}"))
                        .andReturn();
        assertThat(mgrChoice.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrChoice).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        MvcResult mgrRetry =
                mockMvc.perform(
                                post(retryUrl)
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr"))
                                        .header("Idempotency-Key", "mgr-r"))
                        .andReturn();
        assertThat(mgrRetry.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrRetry).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        // 3. Other student -> 404 NOT_FOUND
        MvcResult strChoice =
                performChoice(
                        stranger,
                        ownerAttempt.getId(),
                        p2Id,
                        "str-c",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(strChoice.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(strChoice).path("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult strRetry =
                performRetry(stranger, ownerAttempt.getId(), p2Id, "str-r", null, null);
        assertThat(strRetry.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(strRetry).path("code").asText()).isEqualTo("NOT_FOUND");

        // 4. Foreign partId -> 404 NOT_FOUND
        MvcResult foreignChoice =
                performChoice(
                        owner,
                        ownerAttempt.getId(),
                        foreignPartId,
                        "for-c",
                        ownerSid,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(foreignChoice.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(foreignChoice).path("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult foreignRetry =
                performRetry(owner, ownerAttempt.getId(), foreignPartId, "for-r", ownerSid, null);
        assertThat(foreignRetry.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(foreignRetry).path("code").asText()).isEqualTo("NOT_FOUND");
    }
}
