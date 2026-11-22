package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptResponse;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;

/** Unit tests for {@link AttemptLifecycleService} transaction/invariant guards (R1-9, R1-23, R1-24). */
class AttemptLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptAccessGate accessGate;
    private AttemptRepository attemptRepository;
    private AttemptPartRepository attemptPartRepository;
    private AttemptItemRepository attemptItemRepository;
    private AttemptEventRepository attemptEventRepository;
    private PublishedExamPort publishedExamPort;
    private EntitlementPort entitlementPort;
    private OutboxPublisher outboxPublisher;
    private IdempotentCommand idempotentCommand;
    private OverdueAttemptCloser overdueAttemptCloser;
    private AttemptSessionChecker attemptSessionChecker;
    private FinalAnswerWriter finalAnswerWriter;
    private PlatformTransactionManager transactionManager;
    private AttemptLifecycleService service;

    @BeforeEach
    void setUp() {
        accessGate = mock(AttemptAccessGate.class);
        attemptRepository = mock(AttemptRepository.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        attemptItemRepository = mock(AttemptItemRepository.class);
        attemptEventRepository = mock(AttemptEventRepository.class);
        publishedExamPort = mock(PublishedExamPort.class);
        entitlementPort = mock(EntitlementPort.class);
        outboxPublisher = mock(OutboxPublisher.class);
        idempotentCommand = mock(IdempotentCommand.class);
        overdueAttemptCloser = mock(OverdueAttemptCloser.class);
        attemptSessionChecker = mock(AttemptSessionChecker.class);
        finalAnswerWriter = mock(FinalAnswerWriter.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service =
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
                        PartTransitionHook.NOOP,
                        transactionManager,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void advanceOverdueInLockRequiresActiveTransaction() {
        Attempt attempt = openAttempt(AttemptTiming.SHARED, NOW.minusSeconds(5));
        assertThatThrownBy(() -> service.advanceOverdueInLock(attempt, List.of(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void finishPartThrowsIllegalStateWhenPublishedExamVersionMissing() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        UUID userId = attempt.getUserId();
        AttemptPart p0 = openPart(attempt.getId(), "P1", 0, NOW.plusSeconds(300));
        AttemptPart p1 =
                AttemptPart.createPending(
                        UUID.randomUUID(),
                        attempt.getId(),
                        "P2",
                        "Part 2",
                        1,
                        30,
                        10,
                        BigDecimal.TEN,
                        2L);

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptRepository.existsByIdAndUserId(attempt.getId(), userId)).thenReturn(true);
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attempt.getId()))
                .thenReturn(List.of(p0, p1));
        when(attemptItemRepository.existsByPartId(p1.getId())).thenReturn(false);
        when(publishedExamPort.loadVersion(attempt.getExamId(), attempt.getExamVersionNo()))
                .thenReturn(Optional.empty());

        TransactionStatus txStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any()))
                .thenAnswer(
                        inv -> {
                            TransactionSynchronizationManager.setActualTransactionActive(true);
                            return txStatus;
                        });
        try {
            assertThatThrownBy(() -> service.finishPart(attempt.getId(), p0.getId(), null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing published exam version");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void sharedAdvanceOverdueChoosesEarlierBetweenChoiceTimeoutAndTimeUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            // Case 1: choiceDeadlineAt < attempt.deadlineAt -> CHOICE_TIMEOUT at choiceDeadlineAt
            Instant choiceEarlier = NOW.minusSeconds(120);
            Instant attemptLater = NOW.minusSeconds(30);
            Attempt shared1 = openAttempt(AttemptTiming.SHARED, attemptLater);
            AttemptPart awaiting1 = awaitingChoicePart(shared1.getId(), choiceEarlier);

            boolean mutated1 = service.advanceOverdueInLock(shared1, List.of(awaiting1), NOW);
            assertThat(mutated1).isTrue();
            assertThat(shared1.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(shared1.getEndReason()).isEqualTo(EndReason.CHOICE_TIMEOUT);
            assertThat(shared1.getFinishedAt()).isEqualTo(choiceEarlier);
            verify(entitlementPort).confirmUse(shared1.getId());

            // Case 2: attempt.deadlineAt <= choiceDeadlineAt -> TIME_UP at attempt.deadlineAt
            Instant attemptEarlier = NOW.minusSeconds(120);
            Instant choiceLater = NOW.minusSeconds(30);
            Attempt shared2 = openAttempt(AttemptTiming.SHARED, attemptEarlier);
            AttemptPart awaiting2 = awaitingChoicePart(shared2.getId(), choiceLater);

            boolean mutated2 = service.advanceOverdueInLock(shared2, List.of(awaiting2), NOW);
            assertThat(mutated2).isTrue();
            assertThat(shared2.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(shared2.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(shared2.getFinishedAt()).isEqualTo(attemptEarlier);
            verify(entitlementPort).confirmUse(shared2.getId());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void multiOpenPerPartAdvanceOverdueDoesNotCloseAttemptWhileEarlierPartRemainsOpen() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, NavigationMode.FREE, null);
        Instant p0Deadline = NOW.plusSeconds(1800);
        Instant p1Deadline = NOW.minusSeconds(60);
        AttemptPart p0 = openPart(attempt.getId(), "P1", 0, p0Deadline);
        AttemptPart p1 = openPart(attempt.getId(), "P2", 1, p1Deadline);
        List<AttemptPart> parts = List.of(p0, p1);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            // Last part expired while earlier part is still open -> close only last part, keep attempt IN_PROGRESS.
            boolean firstPass = service.advanceOverdueInLock(attempt, parts, NOW);
            assertThat(firstPass).isTrue();
            assertThat(p1.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p1.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(p1.getClosedAt()).isEqualTo(p1Deadline);
            assertThat(p0.getState()).isEqualTo(PartState.OPEN);
            assertThat(attempt.getState()).isEqualTo(AttemptState.IN_PROGRESS);
            assertThat(attempt.getEndReason()).isNull();

            // Once earlier part also expires, no non-terminal part remains -> close attempt TIME_UP.
            boolean secondPass = service.advanceOverdueInLock(attempt, parts, p0Deadline.plusSeconds(5));
            assertThat(secondPass).isTrue();
            assertThat(p0.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p0.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(p0.getClosedAt()).isEqualTo(p0Deadline);
            assertThat(attempt.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(attempt.getEndReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(attempt.getFinishedAt()).isEqualTo(p0Deadline);
            verify(entitlementPort).confirmUse(attempt.getId());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void submitAttemptOnMultiOpenFreePerPartClosesExpiredPartTimeUpAndRemainingSubmitted() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, NavigationMode.FREE, null);
        UUID userId = attempt.getUserId();
        UUID sessionId = UUID.randomUUID();
        attempt.claimSession(sessionId, NOW.minusSeconds(10));

        Instant p0Deadline = NOW.minusSeconds(60);
        Instant p1Deadline = NOW.plusSeconds(1800);
        AttemptPart p0 = openPart(attempt.getId(), "P1", 0, p0Deadline);
        AttemptPart p1 = openPart(attempt.getId(), "P2", 1, p1Deadline);
        List<AttemptPart> parts = List.of(p0, p1);

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptRepository.existsByIdAndUserId(attempt.getId(), userId)).thenReturn(true);
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attempt.getId()))
                .thenReturn(parts);
        when(idempotentCommand.execute(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.eq(attempt.getId()),
                        any(),
                        org.mockito.ArgumentMatchers.eq(SubmitAttemptResponse.class),
                        any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Supplier<SubmitAttemptResponse> supplier = inv.getArgument(5);
                            return supplier.get();
                        });

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            SubmitAttemptResponse response =
                    service.submitAttempt(
                            attempt.getId(),
                            "sub-multi-per-part",
                            sessionId,
                            new SubmitAttemptRequest(List.of()));

            assertThat(response.state()).isEqualTo(AttemptState.GRADING);
            assertThat(response.endReason()).isEqualTo(EndReason.SUBMITTED);
            assertThat(p0.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p0.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
            assertThat(p0.getClosedAt()).isEqualTo(p0Deadline);
            assertThat(p1.getState()).isEqualTo(PartState.CLOSED);
            assertThat(p1.getCloseReason()).isEqualTo(PartCloseReason.ATTEMPT_SUBMITTED);
            assertThat(p1.getClosedAt()).isEqualTo(NOW);
            verify(entitlementPort).confirmUse(attempt.getId());
            verify(outboxPublisher).publish(org.mockito.ArgumentMatchers.eq("attempt.finished"), any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void finishPartDelegatesToAttemptGuardAndRejectsFreeAttempt() {
        Attempt attempt = openAttempt(AttemptTiming.SHARED, NavigationMode.FREE, NOW.plusSeconds(1800));
        UUID userId = attempt.getUserId();
        UUID sessionId = UUID.randomUUID();
        attempt.claimSession(sessionId, NOW.minusSeconds(10));
        AttemptPart p0 = openPart(attempt.getId(), "P1", 0, NOW.plusSeconds(1800));
        AttemptPart p1 = openPart(attempt.getId(), "P2", 1, NOW.plusSeconds(1800));

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptRepository.existsByIdAndUserId(attempt.getId(), userId)).thenReturn(true);
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attempt.getId()))
                .thenReturn(List.of(p0, p1));

        assertThatThrownBy(() -> service.finishPart(attempt.getId(), p0.getId(), sessionId, null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_FINISH_NOT_ALLOWED");
    }

    private static Attempt openAttempt(AttemptTiming timing, Instant deadlineAt) {
        return openAttempt(timing, NavigationMode.SEQUENTIAL, deadlineAt);
    }

    private static Attempt openAttempt(
            AttemptTiming timing, NavigationMode navigationMode, Instant deadlineAt) {
        Attempt attempt =
                Attempt.start(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test Exam",
                        UUID.randomUUID(),
                        "BCA",
                        "FULL",
                        null,
                        null,
                        timing,
                        navigationMode,
                        Map.of(),
                        1L,
                        UUID.randomUUID(),
                        "SCORE_AND_ANSWERS",
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        "idem-1",
                        NOW.minusSeconds(600));
        attempt.open(1, NOW.minusSeconds(500), deadlineAt);
        return attempt;
    }

    private static AttemptPart openPart(UUID attemptId, String code, int ord, Instant deadlineAt) {
        AttemptPart part =
                AttemptPart.createPending(
                        UUID.randomUUID(),
                        attemptId,
                        code,
                        "Part " + code,
                        ord,
                        30,
                        10,
                        BigDecimal.TEN,
                        1L);
        part.open(NOW.minusSeconds(500), deadlineAt);
        return part;
    }

    private static AttemptPart awaitingChoicePart(UUID attemptId, Instant choiceDeadlineAt) {
        AttemptPart part =
                AttemptPart.createPending(
                        UUID.randomUUID(),
                        attemptId,
                        "P3",
                        "Part 3",
                        2,
                        30,
                        10,
                        BigDecimal.TEN,
                        3L);
        ReflectionTestUtils.setField(part, "state", PartState.AWAITING_CHOICE);
        ReflectionTestUtils.setField(part, "choiceDeadlineAt", choiceDeadlineAt);
        return part;
    }
}
