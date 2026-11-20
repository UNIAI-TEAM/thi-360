package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionResponse;
import vn.unicom.thi360.attempt.api.HeartbeatResponse;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Manages single-device edit sessions, device takeovers, and session heartbeats for in-progress
 * attempts, and supplies the production {@link AttemptSessionChecker} implementation (LT §7.1, F10).
 */
@Service
public class EditSessionService implements AttemptSessionChecker {

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public EditSessionService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptEventRepository attemptEventRepository,
            OverdueAttemptCloser overdueAttemptCloser,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Claims, reclaims, or takes over the single active edit session of an open attempt. */
    public ClaimSessionResponse claimSession(UUID attemptId, ClaimSessionRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        UUID reqSessionId = request == null ? null : request.sessionId();
        boolean takeover = request != null && Boolean.TRUE.equals(request.takeover());

        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        try {
            return txTemplate.execute(
                    status -> executeClaimSession(attemptId, userId, reqSessionId, takeover));
        } catch (ConflictException ex) {
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }
    }

    private ClaimSessionResponse executeClaimSession(
            UUID attemptId, UUID userId, UUID reqSessionId, boolean takeover) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        Instant lockNow = clock.instant();
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            if (attempt.getEndReason() == EndReason.TIME_UP) {
                throw new ConflictException("TIME_UP");
            }
            throw new ConflictException("ATTEMPT_NOT_OPEN");
        }
        if (attempt.getTiming() == AttemptTiming.SHARED
                && attempt.getDeadlineAt() != null
                && !lockNow.isBefore(attempt.getDeadlineAt())) {
            throw new ConflictException("TIME_UP");
        }

        UUID currentSid = attempt.getEditSessionId();
        if (currentSid == null) {
            UUID newSid = reqSessionId != null ? reqSessionId : Ids.newId();
            attempt.claimSession(newSid, lockNow);
            attemptRepository.saveAndFlush(attempt);
            attemptEventRepository.save(
                    new AttemptEvent(
                            Ids.newId(),
                            attempt.getId(),
                            AttemptEventType.SESSION_CLAIMED,
                            newSid,
                            null,
                            lockNow,
                            java.util.Map.of("reclaimed", false)));
            return new ClaimSessionResponse(newSid, lockNow);
        }

        if (currentSid.equals(reqSessionId)) {
            attempt.touchSession(lockNow);
            attemptRepository.saveAndFlush(attempt);
            attemptEventRepository.save(
                    new AttemptEvent(
                            Ids.newId(),
                            attempt.getId(),
                            AttemptEventType.SESSION_CLAIMED,
                            reqSessionId,
                            null,
                            lockNow,
                            java.util.Map.of("reclaimed", true)));
            return new ClaimSessionResponse(reqSessionId, lockNow);
        }

        if (!takeover) {
            throw new AttemptOpenElsewhereException(attempt.getEditSessionSeenAt());
        }

        UUID previousSid = currentSid;
        UUID newSid =
                reqSessionId != null && !reqSessionId.equals(previousSid) ? reqSessionId : Ids.newId();
        attempt.takeoverSession(newSid, lockNow);
        attemptRepository.saveAndFlush(attempt);
        attemptEventRepository.save(
                new AttemptEvent(
                        Ids.newId(),
                        attempt.getId(),
                        AttemptEventType.SESSION_TAKEN_OVER,
                        newSid,
                        null,
                        lockNow,
                        java.util.Map.of("previousSessionId", previousSid.toString())));
        return new ClaimSessionResponse(newSid, lockNow);
    }

    /** Refreshes {@code edit_session_seen_at} for the active edit session and returns current state. */
    public HeartbeatResponse heartbeat(UUID attemptId, UUID sessionId) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        return txTemplate.execute(
                status -> {
                    Attempt attempt =
                            attemptRepository
                                    .findByIdForUpdate(attemptId)
                                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
                    if (!attempt.getUserId().equals(userId)) {
                        throw new NotFoundException("NOT_FOUND", null);
                    }
                    requireEditSession(attempt, sessionId);
                    Instant lockNow = clock.instant();
                    if (attempt.getState() == AttemptState.IN_PROGRESS) {
                        attempt.touchSession(lockNow);
                        attemptRepository.saveAndFlush(attempt);
                    }
                    return new HeartbeatResponse(lockNow, attempt.getState());
                });
    }

    @Override
    public void requireEditSession(Attempt attempt, UUID sessionId) {
        if (sessionId == null
                || attempt.getEditSessionId() == null
                || !attempt.getEditSessionId().equals(sessionId)) {
            throw new ConflictException("ATTEMPT_SESSION_REVOKED");
        }
    }
}
