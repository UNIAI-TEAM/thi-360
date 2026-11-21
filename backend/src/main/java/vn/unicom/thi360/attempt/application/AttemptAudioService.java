package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AttemptAudioController.AttemptAudioPlayResponse;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Coordinates limited-play audio lifecycle ({@code plays}, {@code resume}, {@code progress},
 * {@code end}) under pessimistic {@code attempt_audio_play} row locks and strict lock ordering
 * ({@code attempt_audio_play} first, then plain {@code SELECT} of {@code attempt}/{@code
 * attempt_part}) so concurrent lifecycle transitions never deadlock or lose closed-part mutations
 * (BS §17.2, AC-1..8, AC-11..13, AC-19).
 */
@Service
public class AttemptAudioService {

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptAudioPlayRepository attemptAudioPlayRepository;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final AttemptSessionChecker attemptSessionChecker;
    private final AttemptAudioProperties properties;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    private record TxOutcome(
            AttemptAudioPlayResponse response,
            RuntimeException conflict,
            boolean triggerOverdueClose) {

        static TxOutcome success(AttemptAudioPlayResponse response, boolean triggerOverdueClose) {
            return new TxOutcome(response, null, triggerOverdueClose);
        }

        static TxOutcome conflict(RuntimeException conflict, boolean triggerOverdueClose) {
            return new TxOutcome(null, conflict, triggerOverdueClose);
        }
    }

    private record LockedContext(
            AttemptAudioPlay play,
            Attempt attempt,
            AttemptPart part,
            Instant now,
            boolean closed,
            boolean deadlineReached,
            String closeCode) {}

    public AttemptAudioService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            AttemptAudioProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptAudioPlayRepository = Objects.requireNonNull(attemptAudioPlayRepository);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.attemptSessionChecker = Objects.requireNonNull(attemptSessionChecker);
        this.properties = Objects.requireNonNull(properties);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Opens a new play (up to {@link AttemptAudioPlay#MAX_PLAYS}) or replays an identical {@code requestId}. */
    public AttemptAudioPlayResponse openPlay(
            UUID attemptId, UUID assetId, UUID sessionId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return executeUnderAudioLock(
                attemptId,
                assetId,
                sessionId,
                ctx -> {
                    AttemptAudioPlay play = ctx.play();
                    if (ctx.closed()) {
                        return TxOutcome.conflict(
                                new ConflictException(ctx.closeCode()), ctx.deadlineReached());
                    }
                    if (requestId.equals(play.getLastRequestId())) {
                        return TxOutcome.success(toResponse(play), false);
                    }
                    if (play.getState() == AudioPlayState.PLAYING) {
                        return TxOutcome.conflict(
                                new AudioPlayInProgressException(play.getPlayId()), false);
                    }
                    if (play.getPlayCount() >= AttemptAudioPlay.MAX_PLAYS) {
                        return TxOutcome.conflict(
                                new ConflictException("AUDIO_PLAYS_EXHAUSTED"), false);
                    }
                    Instant now = ctx.now();
                    play.openNewPlay(
                            Ids.newId(), requestId, now, now.plus(properties.leaseDuration()));
                    attemptAudioPlayRepository.saveAndFlush(play);
                    return TxOutcome.success(toResponse(play), false);
                });
    }

    /** Resumes an in-progress play from its saved position and renews its lease without consuming a play. */
    public AttemptAudioPlayResponse resumePlay(
            UUID attemptId, UUID assetId, UUID playId, UUID sessionId) {
        return executeUnderAudioLock(
                attemptId,
                assetId,
                sessionId,
                ctx -> {
                    AttemptAudioPlay play = ctx.play();
                    if (ctx.closed()) {
                        return TxOutcome.conflict(
                                new ConflictException(ctx.closeCode()), ctx.deadlineReached());
                    }
                    if (play.getState() != AudioPlayState.PLAYING
                            || !playId.equals(play.getPlayId())) {
                        return TxOutcome.conflict(new ConflictException("AUDIO_PLAY_STALE"), false);
                    }
                    Instant now = ctx.now();
                    play.resume(now, now.plus(properties.leaseDuration()));
                    attemptAudioPlayRepository.saveAndFlush(play);
                    return TxOutcome.success(toResponse(play), false);
                });
    }

    /** Records validated playback progress against the segment anti-seek budget and extends the lease. */
    public AttemptAudioPlayResponse recordProgress(
            UUID attemptId, UUID assetId, UUID playId, UUID sessionId, long positionMs) {
        return executeUnderAudioLock(
                attemptId,
                assetId,
                sessionId,
                ctx -> {
                    AttemptAudioPlay play = ctx.play();
                    if (ctx.closed()) {
                        return TxOutcome.conflict(
                                new ConflictException(ctx.closeCode()), ctx.deadlineReached());
                    }
                    if (play.getState() != AudioPlayState.PLAYING
                            || !playId.equals(play.getPlayId())) {
                        return TxOutcome.conflict(new ConflictException("AUDIO_PLAY_STALE"), false);
                    }
                    Instant now = ctx.now();
                    long elapsedMs =
                            Math.max(0L, Duration.between(play.getAnchorAt(), now).toMillis());
                    long expectedMs = saturatingAdd(play.getAnchorPositionMs(), elapsedMs);
                    long budgetMs = properties.seekTolerance().toMillis();
                    long remainingToleranceMs = Math.max(0L, budgetMs - play.getToleranceUsedMs());
                    long ceilingMs = saturatingAdd(expectedMs, remainingToleranceMs);
                    if (positionMs < 0
                            || positionMs < play.getPositionMs()
                            || positionMs > play.getDurationMs()
                            || positionMs > ceilingMs) {
                        return TxOutcome.conflict(
                                new AudioSeekRejectedException(play.getPositionMs()), false);
                    }
                    long excessMs = Math.max(0L, positionMs - expectedMs);
                    play.recordProgress(
                            positionMs, excessMs, now, now.plus(properties.leaseDuration()));
                    attemptAudioPlayRepository.saveAndFlush(play);
                    return TxOutcome.success(toResponse(play), false);
                });
    }

    /** Marks the active play finished (or returns its saved terminal state if already ended/closed). */
    public AttemptAudioPlayResponse endPlay(
            UUID attemptId, UUID assetId, UUID playId, UUID sessionId) {
        return executeUnderAudioLock(
                attemptId,
                assetId,
                sessionId,
                ctx -> {
                    AttemptAudioPlay play = ctx.play();
                    if (ctx.closed()) {
                        if (play.getPlayId() != null && play.getPlayId().equals(playId)) {
                            return TxOutcome.success(toResponse(play), ctx.deadlineReached());
                        }
                        return TxOutcome.conflict(
                                new ConflictException("AUDIO_PLAY_STALE"), ctx.deadlineReached());
                    }
                    if (play.getState() == AudioPlayState.PLAYING) {
                        if (!playId.equals(play.getPlayId())) {
                            return TxOutcome.conflict(
                                    new ConflictException("AUDIO_PLAY_STALE"), false);
                        }
                        play.markFinished(ctx.now());
                        attemptAudioPlayRepository.saveAndFlush(play);
                        return TxOutcome.success(toResponse(play), false);
                    }
                    if (play.getState() == AudioPlayState.ENDED) {
                        if (playId.equals(play.getPlayId())) {
                            return TxOutcome.success(toResponse(play), false);
                        }
                        return TxOutcome.conflict(
                                new ConflictException("AUDIO_PLAY_STALE"), false);
                    }
                    return TxOutcome.conflict(new ConflictException("AUDIO_PLAY_STALE"), false);
                });
    }

    private AttemptAudioPlayResponse executeUnderAudioLock(
            UUID attemptId,
            UUID assetId,
            UUID sessionId,
            Function<LockedContext, TxOutcome> handler) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null
                || assetId == null
                || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        TxOutcome outcome =
                txTemplate.execute(
                        status -> {
                            // Lock attempt_audio_play first; then read Attempt/AttemptPart plain to
                            // eliminate deadlock cycles with AttemptLifecycleService (attempt->part->audio).
                            AttemptAudioPlay play =
                                    attemptAudioPlayRepository
                                            .findByAttemptIdAndAssetIdForUpdate(attemptId, assetId)
                                            .orElseThrow(
                                                    () -> new NotFoundException("NOT_FOUND", null));
                            Attempt attempt =
                                    attemptRepository
                                            .findByIdAndUserId(attemptId, userId)
                                            .orElseThrow(
                                                    () -> new NotFoundException("NOT_FOUND", null));
                            AttemptPart part =
                                    attemptPartRepository
                                            .findByIdAndAttemptId(play.getPartId(), attemptId)
                                            .orElseThrow(
                                                    () -> new NotFoundException("NOT_FOUND", null));

                            attemptSessionChecker.requireEditSession(attempt, sessionId);

                            Instant now = clock.instant();
                            boolean liveOpen =
                                    attempt.getState() == AttemptState.IN_PROGRESS
                                            && part.getState() == PartState.OPEN;
                            Instant effectiveDeadline =
                                    attempt.getTiming() == AttemptTiming.PER_PART
                                            ? part.getDeadlineAt()
                                            : attempt.getDeadlineAt();
                            boolean deadlineReached =
                                    liveOpen
                                            && effectiveDeadline != null
                                            && !now.isBefore(effectiveDeadline);
                            boolean closed = !liveOpen || deadlineReached;

                            String closeCode = null;
                            if (closed) {
                                closeCode =
                                        (deadlineReached
                                                        || attempt.getEndReason()
                                                                == EndReason.TIME_UP
                                                        || part.getCloseReason()
                                                                == PartCloseReason.TIME_UP)
                                                ? "TIME_UP"
                                                : "PART_CLOSED";
                                // Persist PLAYING -> ENDED/CLOSED inside tx before returning TxOutcome
                                // (never throw ConflictException after mutating, so Spring commits).
                                if (play.getState() == AudioPlayState.PLAYING) {
                                    play.markClosed(now);
                                    attemptAudioPlayRepository.saveAndFlush(play);
                                }
                            }

                            return handler.apply(
                                    new LockedContext(
                                            play,
                                            attempt,
                                            part,
                                            now,
                                            closed,
                                            deadlineReached,
                                            closeCode));
                        });

        if (outcome == null) {
            throw new IllegalStateException("Audio transaction returned null outcome");
        }
        if (outcome.triggerOverdueClose()) {
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
        }
        if (outcome.conflict() != null) {
            throw outcome.conflict();
        }
        return outcome.response();
    }

    private static AttemptAudioPlayResponse toResponse(AttemptAudioPlay play) {
        return new AttemptAudioPlayResponse(
                play.getPlayId(),
                play.getPlayCount(),
                play.playsLeft(),
                play.getPositionMs(),
                play.getDurationMs(),
                play.getState(),
                play.getEndReason());
    }

    private static long saturatingAdd(long a, long b) {
        if (a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }
}
