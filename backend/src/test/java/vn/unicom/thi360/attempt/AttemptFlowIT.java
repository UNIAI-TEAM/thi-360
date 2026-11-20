package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinishPartResponse;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.application.AttemptAccessGate;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.application.FinalAnswerWriter;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.application.PartTransitionHook;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.api.CreateExamRequest;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamPublishRequest;
import vn.unicom.thi360.exam.api.LoadExamTemplateRequest;
import vn.unicom.thi360.exam.api.UpdateExamListingRequest;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamCheckScope;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Integration tests for sequential part transitions and attempt submission across all exam
 * structures in the DS dataset (AC-1..6, AC-8, AC-12).
 */
class AttemptFlowIT extends AbstractAttemptDsIT {

    @Autowired private AttemptAccessGate accessGate;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private IdempotentCommand idempotentCommand;
    @Autowired private OverdueAttemptCloser overdueAttemptCloser;
    @Autowired private AttemptSessionChecker attemptSessionChecker;
    @Autowired private FinalAnswerWriter finalAnswerWriter;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired private ExamService examService;
    @Autowired private ExamListingService examListingService;
    @Autowired private ExamCheckOrchestrator examCheckOrchestrator;
    @Autowired private ExamPublishService examPublishService;
    @Autowired private UserDirectory userDirectory;

    private record HookInvocation(UUID attemptId, UUID closedPartId, UUID nextPartId, Instant closedAt) {}

    @Test
    void hsaAndQdaOpenPartTwoThenCallHook() throws Exception {
        for (String examCode : List.of("DS-HSA-FULL-MINI", "DS-QDA-FULL-MINI")) {
            List<HookInvocation> hookInvocations = new ArrayList<>();
            PartTransitionHook recordingNoopHook =
                    (attempt, closedPart, nextPart, closedAt) ->
                            hookInvocations.add(
                                    new HookInvocation(
                                            attempt.getId(),
                                            closedPart.getId(),
                                            nextPart.getId(),
                                            closedAt));

            // Construct directly with a recording no-op hook per plan line 3568 so AT-2b merges stay green.
            AttemptLifecycleService customLifecycle =
                    new AttemptLifecycleService(
                            accessGate,
                            attemptRepository,
                            attemptPartRepository,
                            attemptItemRepository,
                            attemptEventRepository,
                            publishedExamPort,
                            entitlementPort,
                            outboxPublisher,
                            idempotentCommand,
                            overdueAttemptCloser,
                            attemptSessionChecker,
                            finalAnswerWriter,
                            recordingNoopHook,
                            transactionManager,
                            mutableClock);

            UUID userId = createStudent("flow-" + examCode);
            GrantOrderResult.Line line = grant(userId, examCode, 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "flow-" + examCode, line.entitlementId(), null);
            assertThat(attempt.getTiming()).isEqualTo(AttemptTiming.PER_PART);

            List<AttemptPart> initialParts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(initialParts).hasSize(3);
            AttemptPart p0 = initialParts.get(0);
            AttemptPart p1 = initialParts.get(1);
            AttemptPart p2 = initialParts.get(2);
            assertThat(p0.getState()).isEqualTo(PartState.OPEN);
            assertThat(p1.getState()).isEqualTo(PartState.PENDING);
            assertThat(p2.getState()).isEqualTo(PartState.PENDING);

            // Finish Part 0 -> Part 1 opens immediately with deadline anchored to Part 0 closed_at.
            mutableClock.advance(Duration.ofMinutes(2));
            Instant t1 = mutableClock.instant();
            FinishPartResponse res0 =
                    runAsStudent(
                            userId,
                            () -> customLifecycle.finishPart(attempt.getId(), p0.getId(), null, null));

            assertThat(res0.attemptState()).isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(res0.closedPart().state()).isEqualTo(PartState.CLOSED);
            assertThat(res0.closedPart().closeReason()).isEqualTo(PartCloseReason.FINISHED);
            assertThat(res0.closedPart().closedAt()).isEqualTo(t1);
            assertThat(res0.nextPart()).isNotNull();
            assertThat(res0.nextPart().partId()).isEqualTo(p1.getId());
            assertThat(res0.nextPart().state()).isEqualTo(PartState.OPEN);
            assertThat(res0.nextPart().startedAt()).isEqualTo(t1);
            assertThat(res0.nextPart().deadlineAt())
                    .isEqualTo(t1.plus(Duration.ofMinutes(p1.getDurationMinutes())));
            assertThat(hookInvocations).isEmpty();

            // Finish Part 1 -> PartTransitionHook invoked once, Part 2 stays PENDING, attempt stays IN_PROGRESS.
            mutableClock.advance(Duration.ofMinutes(1));
            Instant t2 = mutableClock.instant();
            FinishPartResponse res1 =
                    runAsStudent(
                            userId,
                            () -> customLifecycle.finishPart(attempt.getId(), p1.getId(), null, null));

            assertThat(res1.attemptState()).isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(res1.closedPart().state()).isEqualTo(PartState.CLOSED);
            assertThat(res1.closedPart().closedAt()).isEqualTo(t2);
            assertThat(res1.nextPart()).isNotNull();
            assertThat(res1.nextPart().partId()).isEqualTo(p2.getId());
            assertThat(res1.nextPart().state()).isEqualTo(PartState.PENDING);

            assertThat(hookInvocations)
                    .containsExactly(new HookInvocation(attempt.getId(), p1.getId(), p2.getId(), t2));
            assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                    .isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);
            assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isZero();
        }
    }

    @Test
    void bcaThreePartsSharedClock() throws Exception {
        UUID userId = createStudent("flow-bca");
        GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL", 2, List.of());
        Attempt attempt =
                startAndAwaitOpen(
                        userId, "bca-full", line.entitlementId(), new PreChoiceInput("CA2", null));

        assertThat(attempt.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.SEQUENTIAL);
        Instant sharedDeadline = attempt.getStartedAt().plus(Duration.ofMinutes(180));
        assertThat(attempt.getDeadlineAt()).isEqualTo(sharedDeadline);

        int totalUnits =
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId()).stream()
                        .mapToInt(AttemptItem::getUnitCount)
                        .sum();
        assertThat(totalUnits).isEqualTo(81);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        // Part 0 -> finish -> Part 1 OPEN with same shared deadline
        mutableClock.advance(Duration.ofMinutes(20));
        MvcResult f0 = performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null);
        assertThat(f0.getResponse().getStatus()).isEqualTo(200);
        JsonNode j0 = json(f0);
        assertThat(j0.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
        assertThat(j0.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(j0.path("nextPart").path("deadlineAt").asText()))
                .isEqualTo(sharedDeadline);

        // Part 1 -> finish -> Part 2 OPEN with same shared deadline
        mutableClock.advance(Duration.ofMinutes(40));
        MvcResult f1 = performFinishPart(userId, attempt.getId(), parts.get(1).getId(), null, null);
        assertThat(f1.getResponse().getStatus()).isEqualTo(200);
        JsonNode j1 = json(f1);
        assertThat(j1.path("nextPart").path("partId").asText())
                .isEqualTo(parts.get(2).getId().toString());
        assertThat(j1.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(j1.path("nextPart").path("deadlineAt").asText()))
                .isEqualTo(sharedDeadline);

        // Part 2 -> submit -> GRADING
        mutableClock.advance(Duration.ofMinutes(10));
        MvcResult sub = performSubmit(userId, attempt.getId(), "bca-submit-1", null, null);
        assertThat(sub.getResponse().getStatus()).isEqualTo(200);
        JsonNode subJson = json(sub);
        assertThat(subJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(subJson.path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(subJson.path("parts").get(2).path("closeReason").asText())
                .isEqualTo("ATTEMPT_SUBMITTED");
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getDeadlineAt())
                .isEqualTo(sharedDeadline);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void vactThreePartsInOrder() throws Exception {
        UUID userId = createStudent("flow-vact");
        GrantOrderResult.Line line = grant(userId, "DS-VACT-FULL", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "vact-full", line.entitlementId(), null);

        assertThat(attempt.getTiming()).isEqualTo(AttemptTiming.SHARED);
        Instant sharedDeadline = attempt.getStartedAt().plus(Duration.ofMinutes(150));
        assertThat(attempt.getDeadlineAt()).isEqualTo(sharedDeadline);

        int totalUnits =
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId()).stream()
                        .mapToInt(AttemptItem::getUnitCount)
                        .sum();
        assertThat(totalUnits).isEqualTo(120);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        for (int i = 0; i < 2; i++) {
            mutableClock.advance(Duration.ofMinutes(15));
            MvcResult fin = performFinishPart(userId, attempt.getId(), parts.get(i).getId(), null, null);
            assertThat(fin.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(fin).path("nextPart").path("state").asText()).isEqualTo("OPEN");
            assertThat(Instant.parse(json(fin).path("nextPart").path("deadlineAt").asText()))
                    .isEqualTo(sharedDeadline);
        }

        mutableClock.advance(Duration.ofMinutes(15));
        MvcResult sub = performSubmit(userId, attempt.getId(), "vact-sub-1", null, null);
        assertThat(sub.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(sub).path("state").asText()).isEqualTo("GRADING");
        assertThat(json(sub).path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void sp2eMath2025And2026() throws Exception {
        for (String examCode : List.of("DS-SP2E-MATH-2025-MINI", "DS-SP2E-MATH-2026-MINI")) {
            UUID userId = createStudent("flow-" + examCode);
            GrantOrderResult.Line line = grant(userId, examCode, 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "sp2e-" + examCode, line.entitlementId(), null);

            List<AttemptPart> parts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(parts.size()).isGreaterThanOrEqualTo(2);

            for (int i = 0; i < parts.size() - 1; i++) {
                mutableClock.advance(Duration.ofMinutes(1));
                MvcResult fin =
                        performFinishPart(userId, attempt.getId(), parts.get(i).getId(), null, null);
                assertThat(fin.getResponse().getStatus()).isEqualTo(200);
                assertThat(json(fin).path("nextPart").path("partId").asText())
                        .isEqualTo(parts.get(i + 1).getId().toString());
                assertThat(json(fin).path("nextPart").path("state").asText()).isEqualTo("OPEN");
            }

            MvcResult sub = performSubmit(userId, attempt.getId(), "sp2e-sub-" + examCode, null, null);
            assertThat(sub.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(sub).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(sub).path("endReason").asText()).isEqualTo("SUBMITTED");
            assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
        }
    }

    @Test
    void thptqgThreePartsSequential() throws Exception {
        UUID examId = publishThptqgMath2025Exam();
        UUID userId = createStudent("flow-thptqg-seq");
        try {
            int versionNo = publishedExamPort.findServing(examId).orElseThrow().version().versionNo();
            GrantOrderResult.Line line =
                    AttemptTestFixtures.grantExam(
                            grantService, userId, examId, 2, List.of(), "SCORE_AND_ANSWERS", versionNo);
            Attempt attempt = startAndAwaitOpen(userId, "thptqg-3p", line.entitlementId(), null);

            assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.SEQUENTIAL);
            assertThat(attempt.getTiming()).isEqualTo(AttemptTiming.SHARED);
            Instant sharedDeadline = attempt.getStartedAt().plus(Duration.ofMinutes(90));
            assertThat(attempt.getDeadlineAt()).isEqualTo(sharedDeadline);

            List<AttemptPart> parts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(parts).hasSize(3);
            assertThat(parts.get(0).getState()).isEqualTo(PartState.OPEN);
            assertThat(parts.get(1).getState()).isEqualTo(PartState.PENDING);
            assertThat(parts.get(2).getState()).isEqualTo(PartState.PENDING);

            // Finish Part 0 -> Part 1 opens with unchanged shared deadline.
            mutableClock.advance(Duration.ofMinutes(10));
            Instant t1 = mutableClock.instant();
            MvcResult f0 = performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null);
            assertThat(f0.getResponse().getStatus()).isEqualTo(200);
            assertThat(Instant.parse(json(f0).path("nextPart").path("deadlineAt").asText()))
                    .isEqualTo(sharedDeadline);

            // Calling finish on already-closed Part 0 never reopens it or alters Part 1.
            mutableClock.advance(Duration.ofMinutes(5));
            MvcResult f0Replay =
                    performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null);
            assertThat(f0Replay.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(f0Replay).path("closedPart").path("state").asText()).isEqualTo("CLOSED");
            assertThat(Instant.parse(json(f0Replay).path("closedPart").path("closedAt").asText()))
                    .isEqualTo(t1);
            assertThat(json(f0Replay).path("nextPart").path("state").asText()).isEqualTo("OPEN");
            assertThat(Instant.parse(json(f0Replay).path("nextPart").path("startedAt").asText()))
                    .isEqualTo(t1);

            // Finish Part 1 -> Part 2 opens with unchanged shared deadline.
            MvcResult f1 = performFinishPart(userId, attempt.getId(), parts.get(1).getId(), null, null);
            assertThat(f1.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(f1).path("nextPart").path("partId").asText())
                    .isEqualTo(parts.get(2).getId().toString());
            assertThat(Instant.parse(json(f1).path("nextPart").path("deadlineAt").asText()))
                    .isEqualTo(sharedDeadline);

            // Submit -> GRADING.
            MvcResult sub = performSubmit(userId, attempt.getId(), "thptqg-sub", null, null);
            assertThat(sub.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(sub).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(sub).path("endReason").asText()).isEqualTo("SUBMITTED");
            assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
        } finally {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
            createdUserIds.remove(userId);
            cleanupSyntheticExam(examId);
        }
    }

    @Test
    void singlePartScopesSubmitDirectly() throws Exception {
        for (String examCode :
                List.of(
                        "DS-VACT-PART-LANGUAGE-MINI",
                        "DS-VACT-SUBJECT-TOAN-MINI",
                        "DS-THPTQG-MATH-P1-MINI",
                        "DS-HSA-REVIEW-MINI")) {
            UUID userId = createStudent("single-" + examCode);
            GrantOrderResult.Line line = grant(userId, examCode, 2, List.of());

            // 1. Direct submit without calling finish succeeds
            Attempt a1 = startAndAwaitOpen(userId, "s1-" + examCode, line.entitlementId(), null);
            List<AttemptPart> p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
            assertThat(p1).hasSize(1);
            assertThat(p1.getFirst().getState()).isEqualTo(PartState.OPEN);

            MvcResult subRes = performSubmit(userId, a1.getId(), "sub-" + examCode, null, null);
            assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(subRes).path("state").asText()).isEqualTo("GRADING");
            assertThat(json(subRes).path("endReason").asText()).isEqualTo("SUBMITTED");

            // 2. Expiry closes with TIME_UP at deadline
            Attempt a2 = startAndAwaitOpen(userId, "s2-" + examCode, line.entitlementId(), null);
            AttemptPart a2Part =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
            Instant effectiveDeadline =
                    a2.getTiming() == AttemptTiming.SHARED
                            ? a2.getDeadlineAt()
                            : a2Part.getDeadlineAt();
            mutableClock.setAt(effectiveDeadline.plusSeconds(30));

            MvcResult lateSub = performSubmit(userId, a2.getId(), "late-" + examCode, null, null);
            assertThat(lateSub.getResponse().getStatus()).isEqualTo(200);
            JsonNode lateJson = json(lateSub);
            assertThat(lateJson.path("state").asText()).isEqualTo("GRADING");
            assertThat(lateJson.path("endReason").asText()).isEqualTo("TIME_UP");
            assertThat(Instant.parse(lateJson.path("finishedAt").asText()))
                    .isEqualTo(effectiveDeadline);
        }
    }

    @Test
    void closedPartNeverReopens() throws Exception {
        UUID userId = createStudent("closed-noreopen");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "noreopen", line.entitlementId(), null);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p0 = parts.get(0);
        AttemptPart p1 = parts.get(1);

        mutableClock.advance(Duration.ofMinutes(1));
        Instant t1 = mutableClock.instant();
        MvcResult first = performFinishPart(userId, attempt.getId(), p0.getId(), null, null);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        Instant p1Deadline =
                Instant.parse(json(first).path("nextPart").path("deadlineAt").asText());

        // Repeat finish on already-closed Part 0 at a later clock time
        mutableClock.advance(Duration.ofMinutes(1));
        MvcResult replay = performFinishPart(userId, attempt.getId(), p0.getId(), null, null);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode replayJson = json(replay);
        assertThat(replayJson.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
        assertThat(replayJson.path("closedPart").path("closeReason").asText()).isEqualTo("FINISHED");
        assertThat(Instant.parse(replayJson.path("closedPart").path("closedAt").asText()))
                .isEqualTo(t1);
        assertThat(replayJson.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(replayJson.path("nextPart").path("startedAt").asText()))
                .isEqualTo(t1);
        assertThat(Instant.parse(replayJson.path("nextPart").path("deadlineAt").asText()))
                .isEqualTo(p1Deadline);
        assertThat(replayJson.path("results")).isEmpty();

        List<AttemptEvent> events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId());
        long p0ClosedEvents =
                events.stream()
                        .filter(
                                e ->
                                        e.getType() == AttemptEventType.PART_CLOSED
                                                && p0.getId().equals(e.getPartId()))
                        .count();
        long p1OpenedEvents =
                events.stream()
                        .filter(
                                e ->
                                        e.getType() == AttemptEventType.PART_OPENED
                                                && p1.getId().equals(e.getPartId()))
                        .count();
        assertThat(p0ClosedEvents).isEqualTo(1);
        assertThat(p1OpenedEvents).isEqualTo(1);
    }

    @Test
    void submitBeforeLastPartRejected() throws Exception {
        UUID userId = createStudent("submit-early");
        GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt =
                startAndAwaitOpen(
                        userId, "early-sub", line.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        // Part 0 OPEN, Part 1 & 2 PENDING -> 409 PART_NOT_LAST
        MvcResult s0 = performSubmit(userId, attempt.getId(), "early-k1", null, null);
        assertThat(s0.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(s0).path("code").asText()).isEqualTo("PART_NOT_LAST");
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isZero();

        // Finish Part 0 -> Part 1 OPEN, Part 2 PENDING -> still 409 PART_NOT_LAST
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        MvcResult s1 = performSubmit(userId, attempt.getId(), "early-k2", null, null);
        assertThat(s1.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(s1).path("code").asText()).isEqualTo("PART_NOT_LAST");
    }

    @Test
    void finishOnLastPartRejected() throws Exception {
        // 1. Multi-part exam's last part -> 409 ATTEMPT_PART_IS_LAST
        UUID u1 = createStudent("fin-last-multi");
        GrantOrderResult.Line bcaLine = grant(u1, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt multi =
                startAndAwaitOpen(
                        u1, "fin-last-m", bcaLine.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> multiParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(multi.getId());
        assertThat(
                        performFinishPart(u1, multi.getId(), multiParts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performFinishPart(u1, multi.getId(), multiParts.get(1).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult finLastMulti =
                performFinishPart(u1, multi.getId(), multiParts.get(2).getId(), null, null);
        assertThat(finLastMulti.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finLastMulti).path("code").asText()).isEqualTo("ATTEMPT_PART_IS_LAST");
        assertThat(attemptPartRepository.findById(multiParts.get(2).getId()).orElseThrow().getState())
                .isEqualTo(PartState.OPEN);
        assertThat(attemptRepository.findById(multi.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);

        // 2. Single-part exam -> 409 ATTEMPT_PART_IS_LAST
        UUID u2 = createStudent("fin-last-single");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt single = startAndAwaitOpen(u2, "fin-last-s", singleLine.entitlementId(), null);
        AttemptPart onlyPart =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single.getId()).getFirst();

        MvcResult finLastSingle = performFinishPart(u2, single.getId(), onlyPart.getId(), null, null);
        assertThat(finLastSingle.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finLastSingle).path("code").asText()).isEqualTo("ATTEMPT_PART_IS_LAST");
        assertThat(attemptRepository.findById(single.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
    }

    @Test
    void finishAndSubmitRejectInvalidAttemptAndPartStates() throws Exception {
        UUID userId = createStudent("flow-guards");
        GrantOrderResult.Line hsaLine = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());
        Attempt hsa = startAndAwaitOpen(userId, "grd-hsa", hsaLine.entitlementId(), null);
        List<AttemptPart> hsaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsa.getId());

        // 1. Finish on PENDING Part 1 while Part 0 is OPEN -> 409 PART_CLOSED without invoking FinalAnswerWriter
        AtomicInteger writerCalls = new AtomicInteger();
        FinalAnswerWriter countingWriter =
                (attempt, part, changes, now) -> {
                    writerCalls.incrementAndGet();
                    return List.of();
                };
        AttemptLifecycleService guardedLifecycle =
                new AttemptLifecycleService(
                        accessGate,
                        attemptRepository,
                        attemptPartRepository,
                        attemptItemRepository,
                        attemptEventRepository,
                        publishedExamPort,
                        entitlementPort,
                        outboxPublisher,
                        idempotentCommand,
                        overdueAttemptCloser,
                        attemptSessionChecker,
                        countingWriter,
                        PartTransitionHook.NOOP,
                        transactionManager,
                        mutableClock);
        assertThatThrownBy(
                        () ->
                                runAsStudent(
                                        userId,
                                        () ->
                                                guardedLifecycle.finishPart(
                                                        hsa.getId(),
                                                        hsaParts.get(1).getId(),
                                                        null,
                                                        null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_CLOSED");
        assertThat(writerCalls.get()).isZero();
        MvcResult pendingFin = performFinishPart(userId, hsa.getId(), hsaParts.get(1).getId(), null, null);
        assertThat(pendingFin.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(pendingFin).path("code").asText()).isEqualTo("PART_CLOSED");

        // 2. Finish with foreign partId belonging to another attempt -> 404 NOT_FOUND
        GrantOrderResult.Line bcaLine = grant(userId, "DS-BCA-FULL-MINI", 3, List.of());
        Attempt bca =
                startAndAwaitOpen(
                        userId, "grd-bca", bcaLine.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> bcaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(bca.getId());

        MvcResult foreignFin = performFinishPart(userId, hsa.getId(), bcaParts.get(0).getId(), null, null);
        assertThat(foreignFin.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(foreignFin).path("code").asText()).isEqualTo("NOT_FOUND");

        // 3. Finish on SKIPPED Part 1 (non-final) after SHARED TIME_UP -> 409 TIME_UP
        mutableClock.setAt(bca.getDeadlineAt().plusSeconds(10));
        MvcResult skippedFin = performFinishPart(userId, bca.getId(), bcaParts.get(1).getId(), null, null);
        assertThat(skippedFin.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(skippedFin).path("code").asText()).isEqualTo("TIME_UP");

        // 4. Finish and submit on STARTING and ABORTED attempts -> 409 ATTEMPT_NOT_OPEN
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        GrantOrderResult.Line singleLine = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt single = startAndAwaitOpen(userId, "grd-start-abort", singleLine.entitlementId(), null);
        AttemptPart singleP0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(single.getId()).getFirst();

        jdbc.update("UPDATE attempt.attempt SET state = 'STARTING' WHERE id = ?", single.getId());
        MvcResult startingFin = performFinishPart(userId, single.getId(), singleP0.getId(), null, null);
        assertThat(startingFin.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(startingFin).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        MvcResult startingSub = performSubmit(userId, single.getId(), "grd-st-sub", null, null);
        assertThat(startingSub.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(startingSub).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");

        jdbc.update(
                "UPDATE attempt.attempt SET state = 'ABORTED', abort_reason = 'GENERATION_FAILED' WHERE id = ?",
                single.getId());
        MvcResult abortedFin = performFinishPart(userId, single.getId(), singleP0.getId(), null, null);
        assertThat(abortedFin.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(abortedFin).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
        MvcResult abortedSub = performSubmit(userId, single.getId(), "grd-ab-sub", null, null);
        assertThat(abortedSub.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(abortedSub).path("code").asText()).isEqualTo("ATTEMPT_NOT_OPEN");
    }

    private UUID publishThptqgMath2025Exam() {
        UserDirectory.UserSummary actor = userDirectory.findActiveByUsername("sys.admin").orElseThrow();
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(
                new CurrentUserAuthenticationToken(
                        new CurrentUser(
                                actor.id(),
                                actor.username(),
                                Realm.APP,
                                Access.MANAGE,
                                PermissionSet.all()),
                        null));
        SecurityContextHolder.setContext(ctx);
        try {
            String code = "IT-THPTQG-" + UUID.randomUUID().toString().substring(0, 6);
            ExamDetailResponse created =
                    examService.createExam(
                            "create-" + code,
                            new CreateExamRequest(
                                    "THPTQG",
                                    "SUBJECT",
                                    null,
                                    "TOAN",
                                    code,
                                    "THPTQG Toan 3 parts IT",
                                    90,
                                    null,
                                    "THPTQG Math 2025 IT",
                                    null,
                                    "STANDARD",
                                    null));
            if (!"THPTQG_MATH_2025".equals(created.templateCode())) {
                examService.loadTemplate(
                        created.id(),
                        new LoadExamTemplateRequest(
                                "THPTQG_MATH_2025", Boolean.TRUE, "USE_TEMPLATE", created.revision()));
            }
            long revForListing =
                    jdbc.queryForObject(
                            "SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            examListingService.update(
                    created.id(),
                    new UpdateExamListingRequest(
                            "Summary",
                            "Objectives",
                            "Students",
                            50000L,
                            3,
                            true,
                            true,
                            true,
                            null,
                            "SCORE_AND_ANSWERS",
                            "Description",
                            null,
                            revForListing));

            ObjectNode checkResult =
                    examCheckOrchestrator.check(
                            created.id(),
                            ExamCheckScope.from(null, null, null),
                            new ExamCheckCompletion.Actor(
                                    actor.id(), actor.username(), AuditActorType.USER),
                            null);
            assertThat(checkResult.path("status").asText()).isEqualTo("VALID");
            List<String> validPathIds = new ArrayList<>();
            for (JsonNode path : checkResult.path("paths")) {
                if ("VALID".equals(path.path("result").asText())) {
                    validPathIds.add(path.path("pathId").asText());
                }
            }
            assertThat(validPathIds).isNotEmpty();

            long revForPublish =
                    jdbc.queryForObject(
                            "SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            examPublishService.publish(
                    created.id(),
                    "pub-" + code,
                    new ExamPublishRequest(validPathIds, "AT-2a AC-5", revForPublish));
            return created.id();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void cleanupSyntheticExam(UUID examId) {
        jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE exam_id = ?", examId);
        jdbc.update(
                "DELETE FROM shared.outbox_event WHERE payload->>'examId' = ? OR payload->>'targetId' = ?",
                examId.toString(),
                examId.toString());
        jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
    }

    private <T> T runAsStudent(UUID userId, java.util.function.Supplier<T> work) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(
                new CurrentUserAuthenticationToken(
                        new CurrentUser(
                                userId, "learner", Realm.APP, Access.EXAM, PermissionSet.empty()),
                        "test-sid"));
        SecurityContextHolder.setContext(ctx);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
