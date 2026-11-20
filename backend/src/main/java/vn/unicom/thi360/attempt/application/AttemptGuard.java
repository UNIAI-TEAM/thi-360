package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Shared guard for attempt reads and edit commands across AT-1, AT-2a, AT-2b, and AT-3 (LT F11, AC-33).
 *
 * <p>Always verifies ownership first (404 for missing or other user's attempt), then triggers
 * {@link OverdueAttemptCloser#closeIfOverdue} before inspecting state or deadlines.
 */
@Service
public class AttemptGuard {

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final AttemptSessionChecker attemptSessionChecker;
    private final Clock clock;

    public AttemptGuard(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.attemptSessionChecker = Objects.requireNonNull(attemptSessionChecker);
        this.clock = Objects.requireNonNull(clock);
    }

    public record EditablePart(Attempt attempt, AttemptPart part) {
    }

    /** Verifies attempt ownership and closes any overdue state before returning the fresh {@link Attempt}. */
    public Attempt requireOwnedAttempt(UUID attemptId, UUID userId) {
        requireOwnership(attemptId, userId);
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
        return loadOwnedAttempt(attemptId, userId);
    }

    /**
     * Verifies ownership, lazy close, active edit session, open attempt state, and shared deadline.
     *
     * <p>Because {@link AttemptOverdueCloser} joins an active read-write outer transaction
     * ({@code PROPAGATION_REQUIRED}), callers that run inside {@code @Transactional} and throw
     * {@code 409 TIME_UP} should either call this guard before opening the outer transaction or
     * invoke {@link OverdueAttemptCloser#closeIfOverdue} outside the rolled-back transaction
     * (matching {@link AttemptLifecycleService} and {@link AttemptStartService}).
     */
    public Attempt requireEditableAttempt(UUID attemptId, UUID userId, UUID sessionId) {
        requireOwnership(attemptId, userId);
        Instant now = clock.instant();
        overdueAttemptCloser.closeIfOverdue(attemptId, now);
        Attempt attempt = loadOwnedAttempt(attemptId, userId);
        attemptSessionChecker.requireEditSession(attempt, sessionId);
        ensureAttemptOpen(attempt, now);
        return attempt;
    }

    /**
     * Verifies ownership, lazy close, edit session, open attempt, open part, and effective deadline.
     *
     * <p>Because {@link AttemptOverdueCloser} joins an active read-write outer transaction
     * ({@code PROPAGATION_REQUIRED}), callers that run inside {@code @Transactional} and throw
     * {@code 409 TIME_UP} should either call this guard before opening the outer transaction or
     * invoke {@link OverdueAttemptCloser#closeIfOverdue} outside the rolled-back transaction
     * (matching {@link AttemptLifecycleService} and {@link AttemptStartService}).
     */
    public EditablePart requireEditablePart(UUID attemptId, UUID partId, UUID userId, UUID sessionId) {
        requireOwnership(attemptId, userId);
        Instant now = clock.instant();
        overdueAttemptCloser.closeIfOverdue(attemptId, now);
        Attempt attempt = loadOwnedAttempt(attemptId, userId);
        attemptSessionChecker.requireEditSession(attempt, sessionId);
        ensureAttemptOpen(attempt, now);
        if (partId == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        AttemptPart part = attemptPartRepository.findByIdAndAttemptId(partId, attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (part.getState() != PartState.OPEN) {
            if (part.getState() == PartState.CLOSED && part.getCloseReason() == PartCloseReason.TIME_UP) {
                throw new ConflictException("TIME_UP");
            }
            throw new ConflictException("PART_CLOSED");
        }
        Instant effectiveDeadline = attempt.getTiming() == AttemptTiming.PER_PART
                ? part.getDeadlineAt()
                : attempt.getDeadlineAt();
        if (effectiveDeadline != null && !now.isBefore(effectiveDeadline)) {
            throw new ConflictException("TIME_UP");
        }
        return new EditablePart(attempt, part);
    }

    private void requireOwnership(UUID attemptId, UUID userId) {
        if (attemptId == null || userId == null || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
    }

    private Attempt loadOwnedAttempt(UUID attemptId, UUID userId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    private static void ensureAttemptOpen(Attempt attempt, Instant now) {
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            if (attempt.getEndReason() == EndReason.TIME_UP) {
                throw new ConflictException("TIME_UP");
            }
            throw new ConflictException("ATTEMPT_NOT_OPEN");
        }
        if (attempt.getTiming() == AttemptTiming.SHARED
                && attempt.getDeadlineAt() != null
                && !now.isBefore(attempt.getDeadlineAt())) {
            throw new ConflictException("TIME_UP");
        }
    }
}
