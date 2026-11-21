package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.FinishPartResponse;
import vn.unicom.thi360.attempt.api.PartSummaryView;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptResponse;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;

/**
 * Handles sequential part finishing, idempotent attempt submission, shared attempt closing, and
 * deadline-anchored overdue progression (LT §6, §6.3, L7, F7, F9, F11, F19, TM §8.5).
 */
@Service
public class AttemptLifecycleService {

    private static final String SUBMIT_OPERATION = "attempt.submit";
    private static final String OUTBOX_TOPIC_FINISHED = "attempt.finished";

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final PublishedExamPort publishedExamPort;
    private final EntitlementPort entitlementPort;
    private final OutboxPublisher outboxPublisher;
    private final IdempotentCommand idempotentCommand;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final AttemptSessionChecker attemptSessionChecker;
    private final FinalAnswerWriter finalAnswerWriter;
    private final PartTransitionHook partTransitionHook;
    private final AttemptAudioPlayRepository attemptAudioPlayRepository;
    private final AttemptNavigationPolicy navigationPolicy;
    private final AttemptGuard attemptGuard;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    @Autowired
    public AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            ObjectProvider<PartTransitionHook> partTransitionHookProvider,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            AttemptNavigationPolicy navigationPolicy,
            AttemptGuard attemptGuard,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
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
                Objects.requireNonNull(partTransitionHookProvider)
                        .getIfAvailable(() -> PartTransitionHook.NOOP),
                Objects.requireNonNull(
                        attemptAudioPlayRepository,
                        "attemptAudioPlayRepository must not be null"),
                Objects.requireNonNull(navigationPolicy, "navigationPolicy must not be null"),
                Objects.requireNonNull(attemptGuard, "attemptGuard must not be null"),
                transactionManager,
                clock,
                true);
    }

    public AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            ObjectProvider<PartTransitionHook> partTransitionHookProvider,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            AttemptNavigationPolicy navigationPolicy,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
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
                Objects.requireNonNull(partTransitionHookProvider)
                        .getIfAvailable(() -> PartTransitionHook.NOOP),
                Objects.requireNonNull(
                        attemptAudioPlayRepository,
                        "attemptAudioPlayRepository must not be null"),
                Objects.requireNonNull(navigationPolicy, "navigationPolicy must not be null"),
                null,
                transactionManager,
                clock,
                true);
    }

    public AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            ObjectProvider<PartTransitionHook> partTransitionHookProvider,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
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
                Objects.requireNonNull(partTransitionHookProvider)
                        .getIfAvailable(() -> PartTransitionHook.NOOP),
                Objects.requireNonNull(
                        attemptAudioPlayRepository,
                        "attemptAudioPlayRepository must not be null"),
                AttemptNavigationPolicy.defaultPolicy(),
                null,
                transactionManager,
                clock,
                true);
    }

    public AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            PartTransitionHook partTransitionHook,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
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
                partTransitionHook,
                null,
                AttemptNavigationPolicy.defaultPolicy(),
                null,
                transactionManager,
                clock,
                false);
    }

    public AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            PartTransitionHook partTransitionHook,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
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
                partTransitionHook,
                attemptAudioPlayRepository,
                AttemptNavigationPolicy.defaultPolicy(),
                null,
                transactionManager,
                clock,
                true);
    }

    private AttemptLifecycleService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            OutboxPublisher outboxPublisher,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            FinalAnswerWriter finalAnswerWriter,
            PartTransitionHook partTransitionHook,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            AttemptNavigationPolicy navigationPolicy,
            AttemptGuard attemptGuard,
            PlatformTransactionManager transactionManager,
            Clock clock,
            boolean requireAudioPlayRepository) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.attemptSessionChecker = Objects.requireNonNull(attemptSessionChecker);
        this.finalAnswerWriter = Objects.requireNonNull(finalAnswerWriter);
        this.partTransitionHook =
                partTransitionHook == null ? PartTransitionHook.NOOP : partTransitionHook;
        this.attemptAudioPlayRepository =
                requireAudioPlayRepository
                        ? Objects.requireNonNull(
                                attemptAudioPlayRepository,
                                "attemptAudioPlayRepository must not be null")
                        : attemptAudioPlayRepository;
        AttemptNavigationPolicy effectivePolicy =
                navigationPolicy == null ? AttemptNavigationPolicy.defaultPolicy() : navigationPolicy;
        this.navigationPolicy = effectivePolicy;
        this.clock = Objects.requireNonNull(clock);
        this.attemptGuard =
                attemptGuard != null
                        ? attemptGuard
                        : new AttemptGuard(
                                this.attemptRepository,
                                this.attemptPartRepository,
                                this.overdueAttemptCloser,
                                this.attemptSessionChecker,
                                effectivePolicy,
                                this.clock);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    /** Finishes an open non-final sequential part, writes final answers, and opens or hooks the next part. */
    public FinishPartResponse finishPart(
            UUID attemptId, UUID partId, UUID sessionId, FinishPartRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null
                || partId == null
                || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        List<FinalAnswerChange> safeFinalAnswers =
                request == null ? List.of() : request.finalAnswers();
        // Lazy-close in REQUIRES_NEW before opening our tx so overdue closes survive 409 throws below.
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        try {
            return txTemplate.execute(
                    status ->
                            executeFinishPart(
                                    attemptId, userId, partId, sessionId, safeFinalAnswers));
        } catch (ConflictException ex) {
            // If a deadline expired between pre-tx closeIfOverdue and acquiring FOR UPDATE,
            // ensure the overdue close commits outside the rolled-back transaction (R1-11).
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }
    }

    private FinishPartResponse executeFinishPart(
            UUID attemptId,
            UUID userId,
            UUID partId,
            UUID sessionId,
            List<FinalAnswerChange> finalAnswers) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptSessionChecker.requireEditSession(attempt, sessionId);

        if (attempt.getState() == AttemptState.STARTING
                || attempt.getState() == AttemptState.ABORTED) {
            throw new ConflictException("ATTEMPT_NOT_OPEN");
        }

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        int partIdx = indexOfPart(parts, partId);
        if (partIdx < 0) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptGuard.requirePartFinishAllowed(attempt);
        if (partIdx == parts.size() - 1) {
            throw new ConflictException("ATTEMPT_PART_IS_LAST");
        }

        AttemptPart part = parts.get(partIdx);
        AttemptPart nextPart = parts.get(partIdx + 1);

        // Re-sample clock strictly after acquiring FOR UPDATE locks to avoid stale pre-lock timestamps.
        Instant lockNow = clock.instant();
        advanceOverdueInLock(attempt, parts, lockNow);

        if (part.getState() == PartState.CLOSED) {
            return buildFinishPartResponse(lockNow, attempt, part, nextPart, List.of());
        }
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            if (attempt.getEndReason() == EndReason.TIME_UP) {
                throw new ConflictException("TIME_UP");
            }
            throw new ConflictException("PART_CLOSED");
        }
        if (part.getState() != PartState.OPEN) {
            throw new ConflictException("PART_CLOSED");
        }

        List<FinalAnswerResult> results =
                safeResults(finalAnswerWriter.writeFinalAnswers(attempt, part, finalAnswers, lockNow));

        part.close(PartCloseReason.FINISHED, lockNow);
        attemptEventRepository.save(
                new AttemptEvent(
                        Ids.newId(),
                        attempt.getId(),
                        AttemptEventType.PART_CLOSED,
                        sessionId,
                        part.getId(),
                        lockNow,
                        buildPartClosedData(part, PartCloseReason.FINISHED, lockNow, finalAnswers)));

        if (nextPart.getState() == PartState.PENDING) {
            if (requiresAfterStartChoice(attempt, nextPart)) {
                partTransitionHook.onPartClosedBeforeChoice(attempt, part, nextPart, lockNow);
            } else {
                Instant nextDeadline = resolveNextPartDeadline(attempt, nextPart, lockNow);
                nextPart.open(lockNow, nextDeadline);
                attemptEventRepository.save(
                        new AttemptEvent(
                                Ids.newId(),
                                attempt.getId(),
                                AttemptEventType.PART_OPENED,
                                sessionId,
                                nextPart.getId(),
                                lockNow,
                                buildPartOpenedData(nextPart, lockNow)));
            }
        }

        attemptPartRepository.saveAllAndFlush(parts);
        closePlayingAudioForParts(attempt.getId(), List.of(part.getId()), lockNow);
        return buildFinishPartResponse(lockNow, attempt, part, nextPart, results);
    }

    /** Submits an open attempt idempotently, writing final answers before closing if on time. */
    public SubmitAttemptResponse submitAttempt(
            UUID attemptId, String idempotencyKey, UUID sessionId, SubmitAttemptRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        idempotentCommand.requireKey(idempotencyKey);

        SubmitAttemptRequest normalized =
                request == null
                        ? new SubmitAttemptRequest(List.of())
                        : new SubmitAttemptRequest(request.finalAnswers());
        // Lazy-close in REQUIRES_NEW before idempotent tx so overdue close commits even on 409 PART_NOT_LAST.
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        try {
            return idempotentCommand.execute(
                    idempotencyKey,
                    SUBMIT_OPERATION,
                    attemptId,
                    normalized,
                    SubmitAttemptResponse.class,
                    () -> executeSubmit(attemptId, userId, sessionId, normalized));
        } catch (ConflictException ex) {
            // If a deadline expired between pre-tx closeIfOverdue and acquiring FOR UPDATE,
            // ensure the overdue close commits outside the rolled-back transaction (R1-11).
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }
    }

    private SubmitAttemptResponse executeSubmit(
            UUID attemptId,
            UUID userId,
            UUID sessionId,
            SubmitAttemptRequest normalized) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptSessionChecker.requireEditSession(attempt, sessionId);

        if (attempt.getState() == AttemptState.STARTING
                || attempt.getState() == AttemptState.ABORTED) {
            throw new ConflictException("ATTEMPT_NOT_OPEN");
        }

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        // Re-sample clock strictly after acquiring FOR UPDATE locks to avoid stale pre-lock timestamps.
        Instant lockNow = clock.instant();
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return buildSubmitResponse(lockNow, attempt, parts, List.of());
        }

        advanceOverdueInLock(attempt, parts, lockNow);
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return buildSubmitResponse(lockNow, attempt, parts, List.of());
        }

        if (navigationPolicy.requiresLastPartForSubmit(attempt.getNavigationMode())
                && hasUnfinishedPartForSubmit(parts)) {
            throw new ConflictException("PART_NOT_LAST");
        }

        CloseAttemptResult closed =
                closeAttemptInLock(
                        attempt,
                        parts,
                        EndReason.SUBMITTED,
                        lockNow,
                        sessionId,
                        normalized.finalAnswers(),
                        lockNow);
        return buildSubmitResponse(lockNow, closed.attempt(), closed.parts(), closed.results());
    }

    /** Public shared close entry point for AT-2b ({@code CHOICE_TIMEOUT}), AT-5 ({@code FAULT_CLOSED}), and tests. */
    public CloseAttemptResult closeAttempt(UUID attemptId, EndReason endReason, Instant finishedAt) {
        return closeAttempt(attemptId, endReason, finishedAt, null, List.of());
    }

    /** Public shared close entry point with optional {@code sessionId} and {@code finalAnswers}. */
    public CloseAttemptResult closeAttempt(
            UUID attemptId,
            EndReason endReason,
            Instant finishedAt,
            UUID sessionId,
            List<FinalAnswerChange> finalAnswers) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(endReason, "endReason must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        List<FinalAnswerChange> safeChanges =
                finalAnswers == null ? List.of() : List.copyOf(finalAnswers);

        return txTemplate.execute(
                status -> {
                    Attempt attempt =
                            attemptRepository
                                    .findByIdForUpdate(attemptId)
                                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
                    List<AttemptPart> parts =
                            attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
                    if (attempt.getState() != AttemptState.IN_PROGRESS) {
                        return new CloseAttemptResult(attempt, parts, List.of(), false);
                    }
                    return closeAttemptInLock(
                            attempt, parts, endReason, finishedAt, sessionId, safeChanges, clock.instant());
                });
    }

    /**
     * Advances any overdue open or awaiting-choice parts and closes expired attempts while holding
     * pessimistic locks in strict {@code attempt -> attempt_part} order (LT F11).
     *
     * @return {@code true} if at least one part or the attempt transitioned state.
     */
    boolean advanceOverdueInLock(Attempt attempt, List<AttemptPart> parts, Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("advanceOverdueInLock requires an active transaction");
        }
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return false;
        }
        if (attempt.getTiming() == AttemptTiming.SHARED) {
            AttemptPart awaitingExpired = findOverdueAwaitingChoice(parts, now);
            boolean sharedTimeUp =
                    attempt.getDeadlineAt() != null && !now.isBefore(attempt.getDeadlineAt());
            if (awaitingExpired != null
                    && (!sharedTimeUp
                            || awaitingExpired
                                    .getChoiceDeadlineAt()
                                    .isBefore(attempt.getDeadlineAt()))) {
                closeAttemptInLock(
                        attempt,
                        parts,
                        EndReason.CHOICE_TIMEOUT,
                        awaitingExpired.getChoiceDeadlineAt(),
                        null,
                        List.of(),
                        now);
                return true;
            }
            if (sharedTimeUp) {
                closeAttemptInLock(
                        attempt,
                        parts,
                        EndReason.TIME_UP,
                        attempt.getDeadlineAt(),
                        null,
                        List.of(),
                        now);
                return true;
            }
            return false;
        }

        boolean mutated = false;
        List<UUID> closedPartIds = new ArrayList<>();
        while (attempt.getState() == AttemptState.IN_PROGRESS) {
            AttemptPart awaitingExpired = findOverdueAwaitingChoice(parts, now);
            if (awaitingExpired != null) {
                closeAttemptInLock(
                        attempt,
                        parts,
                        EndReason.CHOICE_TIMEOUT,
                        awaitingExpired.getChoiceDeadlineAt(),
                        null,
                        List.of(),
                        now);
                closePlayingAudioForParts(attempt.getId(), closedPartIds, now);
                return true;
            }

            int openIdx = indexOfOverdueOpenPart(parts, now);
            if (openIdx < 0) {
                break;
            }

            AttemptPart openPart = parts.get(openIdx);
            // Always record exact deadline_at, never worker/caller wall time (LT F11, AC-10/11).
            Instant partClosedAt = openPart.getDeadlineAt();
            openPart.close(PartCloseReason.TIME_UP, partClosedAt);
            closedPartIds.add(openPart.getId());
            mutated = true;
            attemptEventRepository.save(
                    new AttemptEvent(
                            Ids.newId(),
                            attempt.getId(),
                            AttemptEventType.PART_CLOSED,
                            null,
                            openPart.getId(),
                            now,
                            buildPartClosedData(openPart, PartCloseReason.TIME_UP, partClosedAt, List.of())));

            int nextIdx = openIdx + 1;
            if (nextIdx < parts.size()) {
                AttemptPart nextPart = parts.get(nextIdx);
                if (nextPart.getState() == PartState.PENDING) {
                    if (requiresAfterStartChoice(attempt, nextPart)) {
                        partTransitionHook.onPartClosedBeforeChoice(
                                attempt, openPart, nextPart, partClosedAt);
                    } else {
                        Instant nextDeadline =
                                nextPart.getDurationMinutes() != null
                                        ? partClosedAt.plus(
                                                Duration.ofMinutes(nextPart.getDurationMinutes()))
                                        : null;
                        nextPart.open(partClosedAt, nextDeadline);
                        attemptEventRepository.save(
                                new AttemptEvent(
                                        Ids.newId(),
                                        attempt.getId(),
                                        AttemptEventType.PART_OPENED,
                                        null,
                                        nextPart.getId(),
                                        now,
                                        buildPartOpenedData(nextPart, partClosedAt)));
                    }
                }
            }
            if (!hasNonTerminalPart(parts)) {
                Instant attemptClosedAt = latestClosedAt(parts, partClosedAt);
                closeAttemptInLock(
                        attempt, parts, EndReason.TIME_UP, attemptClosedAt, null, List.of(), now);
                closePlayingAudioForParts(attempt.getId(), closedPartIds, now);
                return true;
            }
        }

        if (mutated && attempt.getState() == AttemptState.IN_PROGRESS) {
            attemptPartRepository.saveAllAndFlush(parts);
            closePlayingAudioForParts(attempt.getId(), closedPartIds, now);
        }
        return mutated;
    }

    private CloseAttemptResult closeAttemptInLock(
            Attempt attempt,
            List<AttemptPart> parts,
            EndReason endReason,
            Instant finishedAt,
            UUID sessionId,
            List<FinalAnswerChange> safeChanges,
            Instant now) {
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return new CloseAttemptResult(attempt, parts, List.of(), false);
        }

        List<FinalAnswerResult> results = List.of();
        AttemptPart targetPart = null;
        if (endReason == EndReason.SUBMITTED) {
            targetPart = resolveSubmitTargetPart(attempt, parts);
            Instant effectiveDeadline = resolveEffectiveDeadline(attempt, targetPart);
            boolean overdue =
                    findOverdueAwaitingChoice(parts, finishedAt) != null
                            || (attempt.getTiming() == AttemptTiming.PER_PART
                                    && indexOfOverdueOpenPart(parts, finishedAt) >= 0)
                            || (effectiveDeadline != null
                                    && !finishedAt.isBefore(effectiveDeadline));
            if (overdue) {
                advanceOverdueInLock(attempt, parts, finishedAt);
                return new CloseAttemptResult(
                        attempt,
                        parts,
                        List.of(),
                        attempt.getState() != AttemptState.IN_PROGRESS);
            }
            results =
                    safeResults(
                            finalAnswerWriter.writeFinalAnswers(attempt, targetPart, safeChanges, now));
        }

        PartCloseReason partCloseReason = mapPartCloseReason(endReason);
        List<UUID> closedPartIds = new ArrayList<>();
        for (AttemptPart part : parts) {
            if (part.getState() == PartState.OPEN) {
                part.close(partCloseReason, finishedAt);
                closedPartIds.add(part.getId());
                List<FinalAnswerChange> partAuditChanges =
                        endReason == EndReason.SUBMITTED
                                        && targetPart != null
                                        && part.getId().equals(targetPart.getId())
                                ? safeChanges
                                : List.of();
                attemptEventRepository.save(
                        new AttemptEvent(
                                Ids.newId(),
                                attempt.getId(),
                                AttemptEventType.PART_CLOSED,
                                sessionId,
                                part.getId(),
                                now,
                                buildPartClosedData(
                                        part, partCloseReason, finishedAt, partAuditChanges)));
            } else if (part.getState() != PartState.CLOSED && part.getState() != PartState.SKIPPED) {
                part.skip(partCloseReason, finishedAt);
            }
        }

        attempt.close(endReason, finishedAt, now);
        attemptRepository.saveAndFlush(attempt);
        attemptPartRepository.saveAllAndFlush(parts);
        closePlayingAudioForParts(attempt.getId(), closedPartIds, now);

        recordAttemptClosedEvent(attempt, endReason, finishedAt, sessionId, safeChanges, now);
        entitlementPort.confirmUse(attempt.getId());
        outboxPublisher.publish(OUTBOX_TOPIC_FINISHED, new AttemptFinishedEvent(attempt.getId()));
        return new CloseAttemptResult(attempt, parts, results, true);
    }

    private void closePlayingAudioForParts(
            UUID attemptId, List<UUID> closedPartIds, Instant now) {
        if (attemptAudioPlayRepository == null
                || closedPartIds == null
                || closedPartIds.isEmpty()) {
            return;
        }
        List<AttemptAudioPlay> plays =
                attemptAudioPlayRepository.findByAttemptIdAndPartIdInOrderByAssetIdAscForUpdate(
                        attemptId, closedPartIds);
        List<AttemptAudioPlay> changed = new ArrayList<>();
        for (AttemptAudioPlay play : plays) {
            if (play.getState() == AudioPlayState.PLAYING) {
                play.markClosed(now);
                changed.add(play);
            }
        }
        if (!changed.isEmpty()) {
            attemptAudioPlayRepository.saveAllAndFlush(changed);
        }
    }

    private void recordAttemptClosedEvent(
            Attempt attempt,
            EndReason endReason,
            Instant finishedAt,
            UUID sessionId,
            List<FinalAnswerChange> safeChanges,
            Instant now) {
        AttemptEventType eventType =
                switch (endReason) {
                    case SUBMITTED -> AttemptEventType.SUBMITTED;
                    case TIME_UP -> AttemptEventType.TIME_UP;
                    case CHOICE_TIMEOUT -> AttemptEventType.CHOICE_TIMEOUT;
                    case FAULT_CLOSED -> null;
                };
        if (eventType == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endReason", endReason.name());
        data.put("finishedAt", finishedAt.toString());
        if (endReason == EndReason.SUBMITTED && !safeChanges.isEmpty()) {
            data.put("finalAnswers", toAnswerAuditRefs(safeChanges));
        }
        attemptEventRepository.save(
                new AttemptEvent(Ids.newId(), attempt.getId(), eventType, sessionId, null, now, data));
    }

    private boolean requiresAfterStartChoice(Attempt attempt, AttemptPart nextPart) {
        if (attemptItemRepository.existsByPartId(nextPart.getId())) {
            return false;
        }
        return publishedExamPort
                .loadVersion(attempt.getExamId(), attempt.getExamVersionNo())
                .map(v -> AttemptPathResolver.isAfterStartChoicePart(v, nextPart.getPartCode()))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Missing published exam version examId="
                                                + attempt.getExamId()
                                                + " versionNo="
                                                + attempt.getExamVersionNo()));
    }

    private static Instant resolveNextPartDeadline(
            Attempt attempt, AttemptPart nextPart, Instant startedAt) {
        if (attempt.getTiming() == AttemptTiming.SHARED) {
            return attempt.getDeadlineAt();
        }
        return nextPart.getDurationMinutes() != null
                ? startedAt.plus(Duration.ofMinutes(nextPart.getDurationMinutes()))
                : null;
    }

    private AttemptPart resolveSubmitTargetPart(Attempt attempt, List<AttemptPart> parts) {
        if (!navigationPolicy.requiresLastPartForSubmit(attempt.getNavigationMode())) {
            return null;
        }
        List<AttemptPart> openParts =
                parts.stream().filter(p -> p.getState() == PartState.OPEN).toList();
        return openParts.size() == 1 ? openParts.getFirst() : null;
    }

    private static Instant resolveEffectiveDeadline(Attempt attempt, AttemptPart openPart) {
        if (attempt.getTiming() == AttemptTiming.SHARED) {
            return attempt.getDeadlineAt();
        }
        return openPart != null ? openPart.getDeadlineAt() : null;
    }

    private static boolean hasUnfinishedPartForSubmit(List<AttemptPart> parts) {
        if (parts.isEmpty()) {
            return true;
        }
        for (AttemptPart part : parts) {
            PartState st = part.getState();
            if (st == PartState.PENDING
                    || st == PartState.AWAITING_CHOICE
                    || st == PartState.GENERATING
                    || st == PartState.GENERATION_FAILED) {
                return true;
            }
        }
        return parts.getLast().getState() != PartState.OPEN;
    }

    private static AttemptPart findOverdueAwaitingChoice(List<AttemptPart> parts, Instant now) {
        for (AttemptPart part : parts) {
            if (part.getState() == PartState.AWAITING_CHOICE
                    && part.getChoiceDeadlineAt() != null
                    && !now.isBefore(part.getChoiceDeadlineAt())) {
                return part;
            }
        }
        return null;
    }

    private static int indexOfOverdueOpenPart(List<AttemptPart> parts, Instant now) {
        int bestIdx = -1;
        Instant earliestDeadline = null;
        for (int i = 0; i < parts.size(); i++) {
            AttemptPart part = parts.get(i);
            Instant deadline = part.getDeadlineAt();
            if (part.getState() == PartState.OPEN
                    && deadline != null
                    && !now.isBefore(deadline)
                    && (earliestDeadline == null || deadline.isBefore(earliestDeadline))) {
                earliestDeadline = deadline;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static boolean hasNonTerminalPart(List<AttemptPart> parts) {
        for (AttemptPart part : parts) {
            PartState st = part.getState();
            if (st == PartState.OPEN
                    || st == PartState.PENDING
                    || st == PartState.AWAITING_CHOICE
                    || st == PartState.GENERATING
                    || st == PartState.GENERATION_FAILED) {
                return true;
            }
        }
        return false;
    }

    private static Instant latestClosedAt(List<AttemptPart> parts, Instant fallback) {
        Instant latest = fallback;
        for (AttemptPart part : parts) {
            if (part.getClosedAt() != null
                    && (latest == null || part.getClosedAt().isAfter(latest))) {
                latest = part.getClosedAt();
            }
        }
        return latest;
    }

    private static int indexOfPart(List<AttemptPart> parts, UUID partId) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).getId().equals(partId)) {
                return i;
            }
        }
        return -1;
    }

    private static PartCloseReason mapPartCloseReason(EndReason endReason) {
        return switch (endReason) {
            case SUBMITTED -> PartCloseReason.ATTEMPT_SUBMITTED;
            case TIME_UP -> PartCloseReason.TIME_UP;
            case CHOICE_TIMEOUT -> PartCloseReason.CHOICE_TIMEOUT;
            case FAULT_CLOSED -> PartCloseReason.FAULT_CLOSED;
        };
    }

    private static Map<String, Object> buildPartClosedData(
            AttemptPart part,
            PartCloseReason reason,
            Instant closedAt,
            List<FinalAnswerChange> finalAnswers) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partCode", part.getPartCode());
        data.put("ord", part.getOrd());
        data.put("closeReason", reason.name());
        data.put("closedAt", closedAt.toString());
        if (finalAnswers != null && !finalAnswers.isEmpty()) {
            data.put("finalAnswers", toAnswerAuditRefs(finalAnswers));
        }
        return data;
    }

    private static Map<String, Object> buildPartOpenedData(AttemptPart part, Instant startedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partCode", part.getPartCode());
        data.put("ord", part.getOrd());
        data.put("startedAt", startedAt.toString());
        return data;
    }

    private static List<Map<String, Object>> toAnswerAuditRefs(List<FinalAnswerChange> changes) {
        // AC-20: never persist answer contents on attempt_event, strictly itemId and seq.
        return changes.stream()
                .map(c -> Map.<String, Object>of("itemId", c.itemId().toString(), "seq", c.seq()))
                .toList();
    }

    private static List<FinalAnswerResult> safeResults(List<FinalAnswerResult> results) {
        return results == null ? List.of() : List.copyOf(results);
    }

    private static FinishPartResponse buildFinishPartResponse(
            Instant serverNow,
            Attempt attempt,
            AttemptPart closedPart,
            AttemptPart nextPart,
            List<FinalAnswerResult> results) {
        return new FinishPartResponse(
                serverNow,
                attempt.getId(),
                attempt.getState(),
                attempt.getEndReason(),
                attempt.getFinishedAt(),
                PartSummaryView.from(closedPart),
                nextPart == null ? null : PartSummaryView.from(nextPart),
                results);
    }

    private static SubmitAttemptResponse buildSubmitResponse(
            Instant serverNow,
            Attempt attempt,
            List<AttemptPart> parts,
            List<FinalAnswerResult> results) {
        return new SubmitAttemptResponse(
                serverNow,
                attempt.getId(),
                attempt.getState(),
                attempt.getEndReason(),
                attempt.getFinishedAt(),
                parts.stream().map(PartSummaryView::from).toList(),
                results);
    }
}
