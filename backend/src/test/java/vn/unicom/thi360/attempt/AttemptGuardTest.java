package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.unicom.thi360.attempt.application.AttemptGuard;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.application.NoOpAttemptSessionChecker;
import vn.unicom.thi360.attempt.application.NoOpOverdueAttemptCloser;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Unit tests for {@link AttemptGuard} and temporary no-op module ports (AC-33, plan B1). */
class AttemptGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptRepository attemptRepository;
    private AttemptPartRepository attemptPartRepository;
    private OverdueAttemptCloser overdueCloser;
    private AttemptSessionChecker sessionChecker;
    private AttemptGuard guard;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID attemptId;
    private UUID partId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        overdueCloser = mock(OverdueAttemptCloser.class);
        sessionChecker = mock(AttemptSessionChecker.class);
        guard = new AttemptGuard(
                attemptRepository,
                attemptPartRepository,
                overdueCloser,
                sessionChecker,
                Clock.fixed(NOW, ZoneOffset.UTC));

        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        partId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @Test
    void rejectsMissingOrOtherUsersAttemptWith404WithoutCallingCloserOrSessionChecker() {
        when(attemptRepository.existsByIdAndUserId(attemptId, otherUserId)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireOwnedAttempt(attemptId, otherUserId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, otherUserId, sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");

        verifyNoInteractions(overdueCloser, sessionChecker);
    }

    @Test
    void requireOwnedAttemptInvokesOverdueCloserBeforeReturningAttempt() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));

        Attempt loaded = guard.requireOwnedAttempt(attemptId, ownerId);

        assertThat(loaded).isSameAs(attempt);
        InOrder order = inOrder(overdueCloser, attemptRepository);
        order.verify(overdueCloser).closeIfOverdue(attemptId, NOW);
        order.verify(attemptRepository).findByIdAndUserId(attemptId, ownerId);
        verifyNoInteractions(sessionChecker);
    }

    @Test
    void requireEditablePartInvokesCloserBeforeSessionAndStateChecks() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        AttemptPart part = openPart(NOW.plusSeconds(300));
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(part));

        AttemptGuard.EditablePart editable = guard.requireEditablePart(attemptId, partId, ownerId, sessionId);

        assertThat(editable.attempt()).isSameAs(attempt);
        assertThat(editable.part()).isSameAs(part);

        InOrder order = inOrder(overdueCloser, attemptRepository, sessionChecker, attemptPartRepository);
        order.verify(overdueCloser).closeIfOverdue(attemptId, NOW);
        order.verify(attemptRepository).findByIdAndUserId(attemptId, ownerId);
        order.verify(sessionChecker).requireEditSession(attempt, sessionId);
        order.verify(attemptPartRepository).findByIdAndAttemptId(partId, attemptId);
    }

    @Test
    void rejectsAttemptStillStartingWithAttemptNotOpen() {
        Attempt starting = startingAttempt(AttemptTiming.PER_PART);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(starting));

        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "ATTEMPT_NOT_OPEN");
        verify(overdueCloser).closeIfOverdue(attemptId, NOW);
        verify(sessionChecker).requireEditSession(starting, sessionId);
    }

    @Test
    void rejectsClosedAndUnopenedPartsWithPartClosed() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));

        AttemptPart pending = AttemptPart.createPending(
                partId, attemptId, "P2", "Part 2", 2, 60, 20, BigDecimal.TEN, 1L);
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_CLOSED");

        AttemptPart closed = openPart(NOW.plusSeconds(300));
        closed.close(PartCloseReason.FINISHED, NOW.minusSeconds(10));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_CLOSED");

        AttemptPart skipped = AttemptPart.createPending(
                partId, attemptId, "P3", "Part 3", 3, 60, 20, BigDecimal.TEN, 1L);
        skipped.skip(PartCloseReason.ATTEMPT_SUBMITTED, NOW.minusSeconds(10));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(skipped));
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_CLOSED");
    }

    @Test
    void rejectsPartNotBelongingToAttemptWith404() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void rejectsAtOrAfterEffectiveDeadlineForPerPartAndSharedTiming() {
        Attempt perPart = openAttempt(AttemptTiming.PER_PART, null);
        AttemptPart atExactDeadline = openPart(NOW);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(perPart));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(atExactDeadline));

        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");

        AttemptPart pastDeadline = openPart(NOW.minusSeconds(1));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(pastDeadline));
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");

        // SHARED timing checks attempt.deadlineAt even if part.deadlineAt is later/absent
        Attempt sharedExpired = openAttempt(AttemptTiming.SHARED, NOW);
        AttemptPart openPart = openPart(NOW.plusSeconds(600));
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(sharedExpired));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(openPart));

        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");
        assertThatThrownBy(() -> guard.requireEditableAttempt(attemptId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");
    }

    @Test
    void temporaryNoOpPortsPerformNoSideEffects() {
        NoOpOverdueAttemptCloser noOpCloser = new NoOpOverdueAttemptCloser();
        NoOpAttemptSessionChecker noOpSession = new NoOpAttemptSessionChecker();
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        AttemptPart part = openPart(NOW.plusSeconds(60));

        assertThatCode(() -> noOpCloser.closeIfOverdue(attemptId, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> noOpSession.requireEditSession(attempt, null)).doesNotThrowAnyException();
        assertThatCode(() -> noOpSession.requireEditSession(attempt, sessionId)).doesNotThrowAnyException();

        AttemptGuard guardWithNoOps = new AttemptGuard(
                attemptRepository,
                attemptPartRepository,
                noOpCloser,
                noOpSession,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(part));

        assertThat(guardWithNoOps.requireEditablePart(attemptId, partId, ownerId, null).part().getState())
                .isEqualTo(PartState.OPEN);
    }

    @Test
    void rejectsPartAndAttemptClosedByTimeUpWithTimeUpCode() {
        Attempt attempt = openAttempt(AttemptTiming.PER_PART, null);
        when(attemptRepository.existsByIdAndUserId(attemptId, ownerId)).thenReturn(true);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(attempt));

        AttemptPart closedByTimeUp = openPart(NOW.plusSeconds(300));
        closedByTimeUp.close(PartCloseReason.TIME_UP, NOW.minusSeconds(5));
        when(attemptPartRepository.findByIdAndAttemptId(partId, attemptId)).thenReturn(Optional.of(closedByTimeUp));

        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");

        Attempt endedByTimeUp = openAttempt(AttemptTiming.SHARED, NOW.plusSeconds(300));
        ReflectionTestUtils.setField(endedByTimeUp, "state", AttemptState.SCORED);
        ReflectionTestUtils.setField(endedByTimeUp, "endReason", EndReason.TIME_UP);
        when(attemptRepository.findByIdAndUserId(attemptId, ownerId)).thenReturn(Optional.of(endedByTimeUp));

        assertThatThrownBy(() -> guard.requireEditableAttempt(attemptId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");
        assertThatThrownBy(() -> guard.requireEditablePart(attemptId, partId, ownerId, sessionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TIME_UP");
    }

    @Test
    void attemptPartEnforcesStateTransitionGuards() {
        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Part 1", 0, 60, 10, BigDecimal.TEN, 1L);
        assertThatThrownBy(() -> part.close(PartCloseReason.FINISHED, NOW))
                .isInstanceOf(IllegalStateException.class);

        part.open(NOW, NOW.plusSeconds(600));
        assertThatThrownBy(() -> part.open(NOW, NOW.plusSeconds(600)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> part.skip(PartCloseReason.ATTEMPT_SUBMITTED, NOW))
                .isInstanceOf(IllegalStateException.class);

        part.close(PartCloseReason.FINISHED, NOW.plusSeconds(60));
        assertThatThrownBy(() -> part.close(PartCloseReason.FINISHED, NOW.plusSeconds(65)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> part.open(NOW.plusSeconds(70), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> part.skip(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(70)))
                .isInstanceOf(IllegalStateException.class);

        AttemptPart generating = AttemptPart.createPending(
                UUID.randomUUID(), attemptId, "P2", "Part 2", 1, 60, 10, BigDecimal.TEN, 2L);
        ReflectionTestUtils.setField(generating, "state", PartState.GENERATING);
        generating.open(NOW, NOW.plusSeconds(600));
        assertThat(generating.getState()).isEqualTo(PartState.OPEN);
    }

    private Attempt startingAttempt(AttemptTiming timing) {
        return Attempt.start(
                attemptId,
                ownerId,
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Exam",
                UUID.randomUUID(),
                "HSA",
                "FULL",
                null,
                null,
                timing,
                NavigationMode.SEQUENTIAL,
                Map.of(),
                42L,
                UUID.randomUUID(),
                "FULL_SOLUTION",
                "RAW",
                new BigDecimal("150.0000"),
                new BigDecimal("150.0000"),
                "idem-1",
                NOW.minusSeconds(30));
    }

    private Attempt openAttempt(AttemptTiming timing, Instant deadlineAt) {
        Attempt attempt = startingAttempt(timing);
        attempt.open(1, NOW.minusSeconds(20), deadlineAt);
        return attempt;
    }

    private AttemptPart openPart(Instant deadlineAt) {
        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Part 1", 1, 75, 50, BigDecimal.valueOf(50), 99L);
        part.open(NOW.minusSeconds(20), deadlineAt);
        return part;
    }
}
