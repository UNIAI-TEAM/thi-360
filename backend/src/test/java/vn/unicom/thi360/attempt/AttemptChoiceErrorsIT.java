package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for all 6 LT §6.1 choice/retry error codes, {@link ForbiddenKeysAssert}
 * verification, and ownership/role access checks (AC-15, AC-20, AC-21).
 */
class AttemptChoiceErrorsIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void choiceAndRetryErrorCodesMatchSpec() throws Exception {
        UUID userId = createStudent("err-codes");
        GrantOrderResult.Line hsaLine = grant(userId, "DS-HSA-FULL-MINI", 4, List.of());

        // 1. PART_NOT_AWAITING when calling choice or retry on OPEN Part 0 or PENDING Part 2
        Attempt a1 = startAndAwaitOpen(userId, "ec-1", hsaLine.entitlementId(), null);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        MvcResult choiceP0 =
                performChoice(
                        userId,
                        a1.getId(),
                        a1Parts.get(0).getId(),
                        "ec-p0",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(choiceP0.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(choiceP0).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        MvcResult choicePendingP2 =
                performChoice(
                        userId,
                        a1.getId(),
                        a1Parts.get(2).getId(),
                        "ec-p2-pend",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(choicePendingP2.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(choicePendingP2).path("code").asText()).isEqualTo("PART_NOT_AWAITING");

        // Advance a1 to Part 2 AWAITING_CHOICE
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a1.getId(), a1Parts.get(1).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        AttemptPart a1P2 = attemptPartRepository.findById(a1Parts.get(2).getId()).orElseThrow();

        // retry on AWAITING_CHOICE -> 409 PART_NOT_AWAITING
        MvcResult retryAwaiting =
                performRetry(userId, a1.getId(), a1P2.getId(), "ec-ret-await", null, null);
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
                            null,
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
                            null,
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
                                            null,
                                            new ConfirmPartChoiceRequest(
                                                    "SCIENCE", List.of("LY", "HOA", "SU")))
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(202);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            MvcResult retryGenerating =
                    performRetry(userId, a1.getId(), a1P2.getId(), "ec-ret-gen", null, null);
            assertThat(retryGenerating.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(retryGenerating).path("code").asText()).isEqualTo("PART_GENERATING");

            MvcResult diffWhileGen =
                    performChoice(
                            userId,
                            a1.getId(),
                            a1P2.getId(),
                            "ec-diff-gen",
                            null,
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
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(diffOptAfterOpen.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffOptAfterOpen).path("code").asText()).isEqualTo("CHOICE_LOCKED");

        MvcResult diffSubjAfterOpen =
                performChoice(
                        userId,
                        a1.getId(),
                        a1P2.getId(),
                        "ec-diff-open-2",
                        null,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "DIA")));
        assertThat(diffSubjAfterOpen.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffSubjAfterOpen).path("code").asText()).isEqualTo("CHOICE_LOCKED");

        // Close a1 so we can start a2
        assertThat(performSubmit(userId, a1.getId(), "ec-sub-a1", null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        // 5. CHOICE_WINDOW_CLOSED when past 15-minute window
        Attempt a2 = startAndAwaitOpen(userId, "ec-2", hsaLine.entitlementId(), null);
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a2.getId(), a2Parts.get(1).getId(), null, null)
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
                        null,
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
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(windowClosedAgain.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(windowClosedAgain).path("code").asText()).isEqualTo("CHOICE_WINDOW_CLOSED");

        // 6. Bean Validation: subjectCodes size > 10 & retry reason > 200 -> 400 VALIDATION_FAILED
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt a3 = startAndAwaitOpen(userId, "ec-3", hsaLine.entitlementId(), null);
        List<AttemptPart> a3Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId());
        assertThat(performFinishPart(userId, a3.getId(), a3Parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, a3.getId(), a3Parts.get(1).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        MvcResult tooManySubj =
                performChoice(
                        userId,
                        a3.getId(),
                        a3Parts.get(2).getId(),
                        "ec-val-1",
                        null,
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
                        null,
                        new RetryPartRequest("x".repeat(201)));
        assertThat(longReason.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longReason).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void forbiddenKeysAbsent() throws Exception {
        UUID userId = createStudent("choice-forbid");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "cf", line.entitlementId(), null);
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
                        null,
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
                        null,
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
                        null,
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
        List<AttemptPart> ownerParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(ownerAttempt.getId());
        assertThat(
                        performFinishPart(
                                        owner,
                                        ownerAttempt.getId(),
                                        ownerParts.get(0).getId(),
                                        null,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performFinishPart(
                                        owner,
                                        ownerAttempt.getId(),
                                        ownerParts.get(1).getId(),
                                        null,
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
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(foreignChoice.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(foreignChoice).path("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult foreignRetry =
                performRetry(owner, ownerAttempt.getId(), foreignPartId, "for-r", null, null);
        assertThat(foreignRetry.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(foreignRetry).path("code").asText()).isEqualTo("NOT_FOUND");
    }
}
