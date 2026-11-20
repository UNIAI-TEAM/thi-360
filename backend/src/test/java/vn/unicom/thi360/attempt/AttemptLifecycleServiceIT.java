package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.api.FinalAnswerStatus;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.application.CloseAttemptResult;
import vn.unicom.thi360.attempt.application.FinalAnswerWriter;
import vn.unicom.thi360.attempt.domain.AnswerValue;
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
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;

/**
 * Integration tests for {@link AttemptLifecycleService} shared close procedure across all four
 * {@link EndReason}s, outbox transaction atomicity, {@link FinalAnswerWriter} invocation/rollback/late
 * guards, and zero-answer-content event auditing (AC-13, AC-14, AC-20).
 */
class AttemptLifecycleServiceIT extends AbstractAttemptDsIT {

    @Autowired private AttemptLifecycleService lifecycleService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockitoSpyBean private FinalAnswerWriter finalAnswerWriter;
    @MockitoSpyBean private EntitlementPort entitlementPort;
    @MockitoSpyBean private OutboxPublisher outboxPublisher;
    @MockitoSpyBean private AttemptSessionChecker attemptSessionChecker;

    @AfterEach
    void resetSpies() {
        reset(finalAnswerWriter, entitlementPort, outboxPublisher, attemptSessionChecker);
    }

    @Test
    void closeProcedureForEveryReason() throws Exception {
        for (EndReason reason : EndReason.values()) {
            UUID userId = createStudent("close-" + reason.name().toLowerCase());
            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "cls-" + reason, line.entitlementId(), null);
            List<AttemptPart> parts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(parts).hasSize(3);

            // Simulate Part 1 AWAITING_CHOICE and Part 2 GENERATION_FAILED for non-SUBMITTED cases,
            // and keep Part 1 PENDING / Part 2 AWAITING_CHOICE for SUBMITTED to test all non-open states.
            if (reason == EndReason.CHOICE_TIMEOUT) {
                jdbc.update(
                        "UPDATE attempt.attempt_part SET state = 'AWAITING_CHOICE', choice_deadline_at = ? WHERE id = ?",
                        java.sql.Timestamp.from(mutableClock.instant().plusSeconds(60)),
                        parts.get(1).getId());
                jdbc.update(
                        "UPDATE attempt.attempt_part SET state = 'GENERATION_FAILED' WHERE id = ?",
                        parts.get(2).getId());
            } else if (reason == EndReason.FAULT_CLOSED) {
                jdbc.update(
                        "UPDATE attempt.attempt_part SET state = 'GENERATION_FAILED' WHERE id = ?",
                        parts.get(1).getId());
                jdbc.update(
                        "UPDATE attempt.attempt_part SET state = 'AWAITING_CHOICE', choice_deadline_at = ? WHERE id = ?",
                        java.sql.Timestamp.from(mutableClock.instant().plusSeconds(60)),
                        parts.get(2).getId());
            }
            clearInvocations(finalAnswerWriter, entitlementPort, outboxPublisher);

            Instant closeAt = mutableClock.instant().plusSeconds(45);
            CloseAttemptResult result = lifecycleService.closeAttempt(attempt.getId(), reason, closeAt);
            assertThat(result.closedNow()).isTrue();

            Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
            assertThat(closed.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(closed.getEndReason()).isEqualTo(reason);
            assertThat(closed.getFinishedAt()).isEqualTo(closeAt);

            PartCloseReason expectedPartReason =
                    switch (reason) {
                        case SUBMITTED -> PartCloseReason.ATTEMPT_SUBMITTED;
                        case TIME_UP -> PartCloseReason.TIME_UP;
                        case CHOICE_TIMEOUT -> PartCloseReason.CHOICE_TIMEOUT;
                        case FAULT_CLOSED -> PartCloseReason.FAULT_CLOSED;
                    };

            List<AttemptPart> closedParts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            assertThat(closedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
            assertThat(closedParts.get(0).getCloseReason()).isEqualTo(expectedPartReason);
            assertThat(closedParts.get(0).getClosedAt()).isEqualTo(closeAt);

            assertThat(closedParts.get(1).getState()).isEqualTo(PartState.SKIPPED);
            assertThat(closedParts.get(1).getCloseReason()).isEqualTo(expectedPartReason);
            assertThat(closedParts.get(1).getClosedAt()).isEqualTo(closeAt);

            assertThat(closedParts.get(2).getState()).isEqualTo(PartState.SKIPPED);
            assertThat(closedParts.get(2).getCloseReason()).isEqualTo(expectedPartReason);
            assertThat(closedParts.get(2).getClosedAt()).isEqualTo(closeAt);

            verify(entitlementPort, times(1)).confirmUse(attempt.getId());
            assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
            assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);

            String rawPayload =
                    jdbc.queryForObject(
                            "SELECT payload::text FROM shared.outbox_event WHERE topic = 'attempt.finished' AND payload->>'attemptId' = ?",
                            String.class,
                            attempt.getId().toString());
            JsonNode payloadNode = objectMapper.readTree(rawPayload);
            assertThat(payloadNode.size()).isEqualTo(1);
            assertThat(payloadNode.path("attemptId").asText()).isEqualTo(attempt.getId().toString());
        }
    }

    @Test
    void eventIsPublishedInCloseTransaction() throws Exception {
        UUID userId = createStudent("outbox-tx");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "outbox-tx", line.entitlementId(), null);
        clearInvocations(finalAnswerWriter, entitlementPort, outboxPublisher);

        // OutboxPublisher.publish has Propagation.MANDATORY, so configure stub inside an active tx.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        s ->
                                doThrow(new RuntimeException("simulated outbox failure"))
                                        .when(outboxPublisher)
                                        .publish(eq("attempt.finished"), any()));

        Instant t = mutableClock.instant().plusSeconds(10);
        assertThatThrownBy(() -> lifecycleService.closeAttempt(attempt.getId(), EndReason.TIME_UP, t))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated outbox failure");

        // Whole close transaction must roll back when outbox publish fails
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isZero();
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isZero();

        // Clear stub and verify close + outbox commit together
        reset(outboxPublisher);
        CloseAttemptResult ok = lifecycleService.closeAttempt(attempt.getId(), EndReason.TIME_UP, t);
        assertThat(ok.closedNow()).isTrue();
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.GRADING);
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void finalAnswersWrittenBeforeClose() throws Exception {
        // 1. Verify for finishPart
        UUID u1 = createStudent("writer-finish");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt hsaAttempt = startAndAwaitOpen(u1, "wr-fin", hsaLine.entitlementId(), null);
        AttemptPart hsaP0 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsaAttempt.getId()).getFirst();
        UUID hsaItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(hsaP0.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        doAnswer(
                        inv -> {
                            Attempt attArg = inv.getArgument(0);
                            AttemptPart partArg = inv.getArgument(1);
                            assertThat(attArg.getState()).isEqualTo(AttemptState.IN_PROGRESS);
                            assertThat(partArg.getState()).isEqualTo(PartState.OPEN);
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT state FROM attempt.attempt WHERE id = ?",
                                                    String.class,
                                                    hsaAttempt.getId()))
                                    .isEqualTo("IN_PROGRESS");
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT state FROM attempt.attempt_part WHERE id = ?",
                                                    String.class,
                                                    hsaP0.getId()))
                                    .isEqualTo("OPEN");
                            return List.of(
                                    new FinalAnswerResult(
                                            hsaItemId, 7L, FinalAnswerStatus.SAVED, null));
                        })
                .when(finalAnswerWriter)
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && hsaP0.getId().equals(p.getId())),
                        any(),
                        any());

        FinishPartRequest finReq =
                new FinishPartRequest(
                        List.of(
                                new FinalAnswerChange(
                                        hsaItemId, 7L, new AnswerValue.SingleChoice("opt-1"))));
        MvcResult finRes = performFinishPart(u1, hsaAttempt.getId(), hsaP0.getId(), null, finReq);
        assertThat(finRes.getResponse().getStatus()).isEqualTo(200);
        verify(finalAnswerWriter, times(1))
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && hsaP0.getId().equals(p.getId())),
                        any(),
                        any());
        JsonNode finJson = json(finRes);
        assertThat(finJson.path("results")).hasSize(1);
        assertThat(finJson.path("results").get(0).path("itemId").asText())
                .isEqualTo(hsaItemId.toString());
        assertThat(finJson.path("results").get(0).path("seq").asLong()).isEqualTo(7L);
        assertThat(finJson.path("results").get(0).path("status").asText()).isEqualTo("SAVED");

        // 2. Verify for submitAttempt
        reset(finalAnswerWriter);
        UUID u2 = createStudent("writer-submit");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt singleAttempt = startAndAwaitOpen(u2, "wr-sub", singleLine.entitlementId(), null);
        AttemptPart singleP0 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(singleAttempt.getId()).getFirst();
        UUID singleItemId =
                attemptItemRepository.findByPartIdOrderByOrdAsc(singleP0.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        doAnswer(
                        inv -> {
                            Attempt attArg = inv.getArgument(0);
                            AttemptPart partArg = inv.getArgument(1);
                            assertThat(attArg.getState()).isEqualTo(AttemptState.IN_PROGRESS);
                            assertThat(partArg.getState()).isEqualTo(PartState.OPEN);
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT state FROM attempt.attempt WHERE id = ?",
                                                    String.class,
                                                    singleAttempt.getId()))
                                    .isEqualTo("IN_PROGRESS");
                            return List.of(
                                    new FinalAnswerResult(
                                            singleItemId, 9L, FinalAnswerStatus.STALE, null));
                        })
                .when(finalAnswerWriter)
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && singleP0.getId().equals(p.getId())),
                        any(),
                        any());

        SubmitAttemptRequest subReq =
                new SubmitAttemptRequest(
                        List.of(
                                new FinalAnswerChange(
                                        singleItemId, 9L, new AnswerValue.SingleChoice("opt-2"))));
        MvcResult subRes = performSubmit(u2, singleAttempt.getId(), "wr-sub-key", null, subReq);
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
        verify(finalAnswerWriter, times(1))
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && singleP0.getId().equals(p.getId())),
                        any(),
                        any());
        JsonNode subJson = json(subRes);
        assertThat(subJson.path("results")).hasSize(1);
        assertThat(subJson.path("results").get(0).path("itemId").asText())
                .isEqualTo(singleItemId.toString());
        assertThat(subJson.path("results").get(0).path("seq").asLong()).isEqualTo(9L);
        assertThat(subJson.path("results").get(0).path("status").asText()).isEqualTo("STALE");
    }

    @Test
    void writerFailureRollsBack() throws Exception {
        // 1. Writer throws during finish -> part remains OPEN, nextPart remains PENDING, 0 events
        UUID u1 = createStudent("writer-fail-fin");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = startAndAwaitOpen(u1, "wr-fail-fin", hsaLine.entitlementId(), null);
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());
        UUID a1ItemId =
                attemptItemRepository.findByPartIdOrderByOrdAsc(a1Parts.get(0).getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        doThrow(new RuntimeException("simulated finish writer failure"))
                .when(finalAnswerWriter)
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && a1Parts.get(0).getId().equals(p.getId())),
                        any(),
                        any());

        MvcResult finFail =
                performFinishPart(
                        u1,
                        a1.getId(),
                        a1Parts.get(0).getId(),
                        null,
                        new FinishPartRequest(
                                List.of(
                                        new FinalAnswerChange(
                                                a1ItemId, 1L, new AnswerValue.SingleChoice("x")))));
        assertThat(finFail.getResponse().getStatus()).isEqualTo(500);
        assertThat(attemptRepository.findById(a1.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(attemptPartRepository.findById(a1Parts.get(0).getId()).orElseThrow().getState())
                .isEqualTo(PartState.OPEN);
        assertThat(attemptPartRepository.findById(a1Parts.get(1).getId()).orElseThrow().getState())
                .isEqualTo(PartState.PENDING);

        // 2. Writer throws during submit -> attempt remains IN_PROGRESS, RESERVED, 0 outbox, idempotency key NOT saved
        reset(finalAnswerWriter);
        UUID u2 = createStudent("writer-fail-sub");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt a2 = startAndAwaitOpen(u2, "wr-fail-sub", singleLine.entitlementId(), null);
        AttemptPart a2Part = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
        UUID a2ItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(a2Part.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        doThrow(new RuntimeException("simulated submit writer failure"))
                .when(finalAnswerWriter)
                .writeFinalAnswers(
                        any(),
                        argThat(p -> p != null && a2Part.getId().equals(p.getId())),
                        any(),
                        any());

        SubmitAttemptRequest req =
                new SubmitAttemptRequest(
                        List.of(
                                new FinalAnswerChange(
                                        a2ItemId, 2L, new AnswerValue.SingleChoice("y"))));
        MvcResult subFail = performSubmit(u2, a2.getId(), "fail-then-retry", null, req);
        assertThat(subFail.getResponse().getStatus()).isEqualTo(500);
        assertThat(attemptRepository.findById(a2.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(attemptPartRepository.findById(a2Part.getId()).orElseThrow().getState())
                .isEqualTo(PartState.OPEN);
        assertThat(countEntitlementUsage(a2.getId(), "RESERVED")).isEqualTo(1);
        assertThat(countEntitlementUsage(a2.getId(), "USED")).isZero();
        assertThat(countOutboxEvents(a2.getId(), "attempt.finished")).isZero();

        // Retrying with same Idempotency-Key after clearing failure succeeds cleanly
        reset(finalAnswerWriter);
        MvcResult subRetry = performSubmit(u2, a2.getId(), "fail-then-retry", null, req);
        assertThat(subRetry.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(subRetry).path("state").asText()).isEqualTo("GRADING");
        assertThat(countEntitlementUsage(a2.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(a2.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void lateRequestSkipsWriter() throws Exception {
        UUID u1 = createStudent("writer-late-fin");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt a1 = startAndAwaitOpen(u1, "late-fin", hsaLine.entitlementId(), null);
        AttemptPart a1P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst();
        UUID a1ItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(a1P0.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        mutableClock.setAt(a1P0.getDeadlineAt().plus(Duration.ofMinutes(1)));
        MvcResult lateFin =
                performFinishPart(
                        u1,
                        a1.getId(),
                        a1P0.getId(),
                        null,
                        new FinishPartRequest(
                                List.of(
                                        new FinalAnswerChange(
                                                a1ItemId, 1L, new AnswerValue.SingleChoice("late")))));
        assertThat(lateFin.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(lateFin).path("closedPart").path("closeReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(lateFin).path("closedPart").path("closedAt").asText()))
                .isEqualTo(a1P0.getDeadlineAt());
        verifyNoInteractions(finalAnswerWriter);

        // Late submit via endpoint
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("writer-late-sub");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 3, List.of());
        Attempt a2 = startAndAwaitOpen(u2, "late-sub", singleLine.entitlementId(), null);
        AttemptPart a2P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
        UUID a2ItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(a2P0.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        mutableClock.setAt(a2P0.getDeadlineAt());
        MvcResult lateSub =
                performSubmit(
                        u2,
                        a2.getId(),
                        "late-sub-k",
                        null,
                        new SubmitAttemptRequest(
                                List.of(
                                        new FinalAnswerChange(
                                                a2ItemId, 1L, new AnswerValue.SingleChoice("late")))));
        assertThat(lateSub.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(lateSub).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(lateSub).path("finishedAt").asText()))
                .isEqualTo(a2P0.getDeadlineAt());
        verifyNoInteractions(finalAnswerWriter);

        // Direct closeAttempt(SUBMITTED) with late finishedAt also skips FinalAnswerWriter and closes TIME_UP
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt a3 = startAndAwaitOpen(u2, "late-direct", singleLine.entitlementId(), null);
        AttemptPart a3P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a3.getId()).getFirst();
        UUID a3ItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(a3P0.getId()).getFirst().getId();
        clearInvocations(finalAnswerWriter);

        CloseAttemptResult directLate =
                lifecycleService.closeAttempt(
                        a3.getId(),
                        EndReason.SUBMITTED,
                        a3P0.getDeadlineAt().plusSeconds(10),
                        null,
                        List.of(
                                new FinalAnswerChange(
                                        a3ItemId, 5L, new AnswerValue.SingleChoice("late"))));
        assertThat(directLate.closedNow()).isTrue();
        assertThat(directLate.attempt().getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(directLate.attempt().getFinishedAt()).isEqualTo(a3P0.getDeadlineAt());
        verify(finalAnswerWriter, never()).writeFinalAnswers(any(), any(), any(), any());
    }

    @Test
    void eventsCarryNoAnswerContent() throws Exception {
        UUID userId = createStudent("events-no-content");
        GrantOrderResult.Line bcaLine = grant(userId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt a1 =
                startAndAwaitOpen(
                        userId, "evt-no-cnt", bcaLine.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());

        List<AttemptItem> p0Items = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId());
        List<AttemptItem> p2Items = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(2).getId());
        UUID p0ItemId = p0Items.getFirst().getId();
        UUID p2ItemId = p2Items.getFirst().getId();

        String sentinel = "SENTINEL_SECRET_ANSWER_XYZ";
        assertThat(
                        performFinishPart(
                                        userId,
                                        a1.getId(),
                                        parts.get(0).getId(),
                                        null,
                                        new FinishPartRequest(
                                                List.of(
                                                        new FinalAnswerChange(
                                                                p0ItemId,
                                                                11L,
                                                                new AnswerValue.Essay(
                                                                        "<p>" + sentinel + "</p>",
                                                                        1)))))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        assertThat(
                        performFinishPart(userId, a1.getId(), parts.get(1).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        assertThat(
                        performSubmit(
                                        userId,
                                        a1.getId(),
                                        "evt-sub-key",
                                        null,
                                        new SubmitAttemptRequest(
                                                List.of(
                                                        new FinalAnswerChange(
                                                                p2ItemId,
                                                                22L,
                                                                new AnswerValue.ShortAnswer(
                                                                        sentinel)))))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        List<AttemptEvent> a1Events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(a1.getId());
        assertThat(a1Events)
                .extracting(AttemptEvent::getType)
                .contains(
                        AttemptEventType.PART_OPENED,
                        AttemptEventType.PART_CLOSED,
                        AttemptEventType.SUBMITTED);

        boolean sawFinishRef = false;
        boolean sawSubmitRef = false;
        for (AttemptEvent evt : a1Events) {
            String rawData = objectMapper.writeValueAsString(evt.getData());
            assertThat(rawData).doesNotContain(sentinel);
            assertThat(rawData).doesNotContain("\"answer\"");
            assertThat(rawData).doesNotContain("\"html\"");
            assertThat(rawData).doesNotContain("\"text\"");
            assertThat(rawData).doesNotContain("\"optionId\"");
            if (evt.getType() == AttemptEventType.PART_CLOSED && parts.get(0).getId().equals(evt.getPartId())) {
                JsonNode refs = objectMapper.readTree(rawData).path("finalAnswers");
                assertThat(refs).hasSize(1);
                assertThat(refs.get(0).path("itemId").asText()).isEqualTo(p0ItemId.toString());
                assertThat(refs.get(0).path("seq").asLong()).isEqualTo(11L);
                assertThat(refs.get(0).size()).isEqualTo(2);
                sawFinishRef = true;
            }
            if (evt.getType() == AttemptEventType.SUBMITTED) {
                JsonNode refs = objectMapper.readTree(rawData).path("finalAnswers");
                assertThat(refs).hasSize(1);
                assertThat(refs.get(0).path("itemId").asText()).isEqualTo(p2ItemId.toString());
                assertThat(refs.get(0).path("seq").asLong()).isEqualTo(22L);
                assertThat(refs.get(0).size()).isEqualTo(2);
                sawSubmitRef = true;
            }
        }
        assertThat(sawFinishRef).isTrue();
        assertThat(sawSubmitRef).isTrue();

        // Also trigger a TIME_UP close and inspect its events
        Attempt a2 =
                startAndAwaitOpen(
                        userId, "evt-timeout", bcaLine.entitlementId(), new PreChoiceInput("CA2", null));
        mutableClock.setAt(a2.getDeadlineAt().plusSeconds(15));
        assertThat(performGetState(userId, a2.getId()).getResponse().getStatus()).isEqualTo(200);
        List<AttemptEvent> a2Events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(a2.getId());
        assertThat(a2Events).extracting(AttemptEvent::getType).contains(AttemptEventType.TIME_UP);
        for (AttemptEvent evt : a2Events) {
            String rawData = objectMapper.writeValueAsString(evt.getData());
            assertThat(rawData).doesNotContain(sentinel);
        }
    }

    @Test
    void directCloseSubmittedPastAwaitingChoiceDeadlineSkipsWriter() throws Exception {
        UUID userId = createStudent("direct-awaiting-choice");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "dir-ac", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        UUID itemId = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).getFirst().getId();

        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        Instant choiceDeadline = mutableClock.instant().plus(Duration.ofMinutes(10));
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'CLOSED', close_reason = 'FINISHED', closed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(mutableClock.instant()),
                parts.get(1).getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'AWAITING_CHOICE', choice_deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(choiceDeadline),
                parts.get(2).getId());
        clearInvocations(finalAnswerWriter);

        CloseAttemptResult res =
                lifecycleService.closeAttempt(
                        attempt.getId(),
                        EndReason.SUBMITTED,
                        choiceDeadline.plusSeconds(10),
                        null,
                        List.of(
                                new FinalAnswerChange(
                                        itemId, 1L, new AnswerValue.SingleChoice("late"))));
        assertThat(res.closedNow()).isTrue();
        assertThat(res.attempt().getState()).isEqualTo(AttemptState.GRADING);
        assertThat(res.attempt().getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
        assertThat(res.attempt().getFinishedAt()).isEqualTo(choiceDeadline);
        verifyNoInteractions(finalAnswerWriter);
    }

    @Test
    void freeModeSubmitAttachesFinalAnswersOnlyToSubmittedEvent() throws Exception {
        UUID userId = createStudent("free-mode-dedup");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "free-dedup", line.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        UUID itemId = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).getFirst().getId();

        jdbc.update("UPDATE attempt.attempt SET navigation_mode = 'FREE' WHERE id = ?", attempt.getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'OPEN', started_at = ? WHERE id = ?",
                java.sql.Timestamp.from(mutableClock.instant()),
                parts.get(1).getId());

        UUID sessionId = UUID.randomUUID();
        Instant closeAt = mutableClock.instant().plusSeconds(30);
        CloseAttemptResult res =
                lifecycleService.closeAttempt(
                        attempt.getId(),
                        EndReason.SUBMITTED,
                        closeAt,
                        sessionId,
                        List.of(
                                new FinalAnswerChange(
                                        itemId, 5L, new AnswerValue.SingleChoice("opt-1"))));
        assertThat(res.closedNow()).isTrue();

        List<AttemptEvent> events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId());
        List<AttemptEvent> partClosedEvents =
                events.stream().filter(e -> e.getType() == AttemptEventType.PART_CLOSED).toList();
        assertThat(partClosedEvents).hasSize(2);
        for (AttemptEvent partClosed : partClosedEvents) {
            assertThat(partClosed.getData()).doesNotContainKey("finalAnswers");
        }
        AttemptEvent submittedEvent =
                events.stream()
                        .filter(e -> e.getType() == AttemptEventType.SUBMITTED)
                        .findFirst()
                        .orElseThrow();
        assertThat(submittedEvent.getData()).containsKey("finalAnswers");
    }

    @Test
    void forwardsAttemptSessionIdAndRejectsRevokedSession() throws Exception {
        UUID userId = createStudent("session-checker");
        GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL-MINI", 3, List.of());
        Attempt attempt =
                startAndAwaitOpen(
                        userId, "sess-ok", line.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        UUID activeSessionId = UUID.randomUUID();
        clearInvocations(attemptSessionChecker);

        assertThat(
                        performFinishPart(
                                        userId,
                                        attempt.getId(),
                                        parts.get(0).getId(),
                                        activeSessionId,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performFinishPart(
                                        userId,
                                        attempt.getId(),
                                        parts.get(1).getId(),
                                        activeSessionId,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performSubmit(
                                        userId,
                                        attempt.getId(),
                                        "sess-sub-ok",
                                        activeSessionId,
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        verify(attemptSessionChecker, times(3))
                .requireEditSession(argThat(a -> a.getId().equals(attempt.getId())), eq(activeSessionId));

        List<AttemptEvent> events =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId());
        assertThat(events)
                .filteredOn(e -> e.getType() == AttemptEventType.PART_CLOSED)
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));
        assertThat(events)
                .filteredOn(
                        e ->
                                e.getType() == AttemptEventType.PART_OPENED
                                        && !parts.get(0).getId().equals(e.getPartId()))
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));
        assertThat(events)
                .filteredOn(e -> e.getType() == AttemptEventType.SUBMITTED)
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getSessionId()).isEqualTo(activeSessionId));

        // Revoked session -> 409 ATTEMPT_SESSION_REVOKED without mutating state
        Attempt a2 =
                startAndAwaitOpen(
                        userId, "sess-rev", line.entitlementId(), new PreChoiceInput("CA2", null));
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        UUID revokedSessionId = UUID.randomUUID();
        doThrow(new ConflictException("ATTEMPT_SESSION_REVOKED"))
                .when(attemptSessionChecker)
                .requireEditSession(argThat(a -> a.getId().equals(a2.getId())), eq(revokedSessionId));

        MvcResult finRev =
                performFinishPart(
                        userId, a2.getId(), a2Parts.get(0).getId(), revokedSessionId, null);
        assertThat(finRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptPartRepository.findById(a2Parts.get(0).getId()).orElseThrow().getState())
                .isEqualTo(PartState.OPEN);
        assertThat(attemptPartRepository.findById(a2Parts.get(1).getId()).orElseThrow().getState())
                .isEqualTo(PartState.PENDING);

        MvcResult subRev =
                performSubmit(userId, a2.getId(), "sess-sub-rev", revokedSessionId, null);
        assertThat(subRev.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(subRev).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptRepository.findById(a2.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
    }

    @Test
    void directCloseSubmittedPerPartFreeWithOverdueOpenPartSkipsWriter() throws Exception {
        // 1. Multi-part PER_PART FREE attempt with overdue Part 0 OPEN -> closes Part 0 TIME_UP without calling writer
        UUID userId = createStudent("perpart-free-overdue");
        GrantOrderResult.Line fullLine = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt full = startAndAwaitOpen(userId, "pp-free-multi", fullLine.entitlementId(), null);
        List<AttemptPart> fullParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(full.getId());
        UUID itemId = attemptItemRepository.findByPartIdOrderByOrdAsc(fullParts.get(0).getId()).getFirst().getId();
        Instant p0Deadline = fullParts.get(0).getDeadlineAt();

        jdbc.update("UPDATE attempt.attempt SET navigation_mode = 'FREE' WHERE id = ?", full.getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'OPEN', started_at = ?, deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(mutableClock.instant()),
                java.sql.Timestamp.from(p0Deadline.plus(Duration.ofMinutes(30))),
                fullParts.get(1).getId());
        clearInvocations(finalAnswerWriter);

        CloseAttemptResult multiRes =
                lifecycleService.closeAttempt(
                        full.getId(),
                        EndReason.SUBMITTED,
                        p0Deadline.plusSeconds(10),
                        null,
                        List.of(new FinalAnswerChange(itemId, 1L, new AnswerValue.SingleChoice("late"))));
        assertThat(multiRes.closedNow()).isFalse();
        verifyNoInteractions(finalAnswerWriter);

        List<AttemptPart> updatedParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(full.getId());
        assertThat(updatedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updatedParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(updatedParts.get(0).getClosedAt()).isEqualTo(p0Deadline);
        assertThat(updatedParts.get(1).getState()).isEqualTo(PartState.OPEN);
        assertThat(attemptRepository.findById(full.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);

        // 2. Single-part PER_PART FREE attempt past deadline -> closes attempt TIME_UP without calling writer
        GrantOrderResult.Line singleLine = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt single = startAndAwaitOpen(userId, "pp-free-single", singleLine.entitlementId(), null);
        AttemptPart singlePart = attemptPartRepository.findByAttemptIdOrderByOrdAsc(single.getId()).getFirst();
        UUID singleItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(singlePart.getId()).getFirst().getId();
        Instant singleDeadline = singlePart.getDeadlineAt();

        jdbc.update("UPDATE attempt.attempt SET navigation_mode = 'FREE' WHERE id = ?", single.getId());
        clearInvocations(finalAnswerWriter);

        CloseAttemptResult singleRes =
                lifecycleService.closeAttempt(
                        single.getId(),
                        EndReason.SUBMITTED,
                        singleDeadline.plusSeconds(10),
                        null,
                        List.of(new FinalAnswerChange(singleItemId, 2L, new AnswerValue.SingleChoice("late"))));
        assertThat(singleRes.closedNow()).isTrue();
        assertThat(singleRes.attempt().getState()).isEqualTo(AttemptState.GRADING);
        assertThat(singleRes.attempt().getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(singleRes.attempt().getFinishedAt()).isEqualTo(singleDeadline);
        verifyNoInteractions(finalAnswerWriter);
    }
}
