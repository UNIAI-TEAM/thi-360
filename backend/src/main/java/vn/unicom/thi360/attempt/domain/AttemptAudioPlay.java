package vn.unicom.thi360.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks playback count, active play lease, and anti-seek tolerance budget for one audio asset
 * within an attempt (BS §17.2).
 */
@Entity
@Table(name = "attempt_audio_play", schema = "attempt")
public class AttemptAudioPlay {

    public static final int MAX_PLAYS = 2;

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "part_id", nullable = false, updatable = false)
    private UUID partId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "play_id")
    private UUID playId;

    @Column(name = "last_request_id")
    private String lastRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private AudioPlayState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private AudioEndReason endReason;

    @Column(name = "position_ms", nullable = false)
    private long positionMs;

    @Column(name = "anchor_at")
    private Instant anchorAt;

    @Column(name = "anchor_position_ms", nullable = false)
    private long anchorPositionMs;

    @Column(name = "segment_excess_ms", nullable = false)
    private long segmentExcessMs;

    @Column(name = "tolerance_used_ms", nullable = false)
    private long toleranceUsedMs;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttemptAudioPlay() {
    }

    private AttemptAudioPlay(
            UUID id,
            UUID attemptId,
            UUID partId,
            UUID assetId,
            long durationMs,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId must not be null");
        this.partId = Objects.requireNonNull(partId, "partId must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        this.durationMs = durationMs;
        this.playCount = 0;
        this.state = AudioPlayState.IDLE;
        this.positionMs = 0L;
        this.anchorPositionMs = 0L;
        this.segmentExcessMs = 0L;
        this.toleranceUsedMs = 0L;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /** Creates an initial {@link AudioPlayState#IDLE} row for an audio asset in a generated paper. */
    public static AttemptAudioPlay createIdle(
            UUID id, UUID attemptId, UUID partId, UUID assetId, long durationMs, Instant now) {
        return new AttemptAudioPlay(id, attemptId, partId, assetId, durationMs, now);
    }

    public int playsLeft() {
        return Math.max(0, MAX_PLAYS - this.playCount);
    }

    /** Opens a fresh playback up to {@link #MAX_PLAYS}, resetting position and seek tolerance. */
    public void openNewPlay(UUID newPlayId, String requestId, Instant now, Instant leaseUntil) {
        if (this.state == AudioPlayState.PLAYING) {
            throw new IllegalStateException("Audio play is already in progress");
        }
        if (this.playCount >= MAX_PLAYS) {
            throw new IllegalStateException("Audio play limit reached");
        }
        this.playId = Objects.requireNonNull(newPlayId, "newPlayId must not be null");
        this.lastRequestId = Objects.requireNonNull(requestId, "requestId must not be null");
        this.playCount = this.playCount + 1;
        this.state = AudioPlayState.PLAYING;
        this.endReason = null;
        this.positionMs = 0L;
        this.anchorAt = Objects.requireNonNull(now, "now must not be null");
        this.anchorPositionMs = 0L;
        this.segmentExcessMs = 0L;
        this.toleranceUsedMs = 0L;
        this.leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        this.updatedAt = now;
    }

    /** Starts a new playback segment from the saved position without consuming an extra play. */
    public void resume(Instant now, Instant leaseUntil) {
        if (this.state != AudioPlayState.PLAYING) {
            throw new IllegalStateException("Cannot resume audio from state " + this.state);
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        this.toleranceUsedMs = this.toleranceUsedMs + this.segmentExcessMs;
        this.segmentExcessMs = 0L;
        this.anchorAt = now;
        this.anchorPositionMs = this.positionMs;
        this.leaseUntil = leaseUntil;
        this.updatedAt = now;
    }

    /** Advances saved playback position and extends the lease; finishes when reaching duration. */
    public void recordProgress(
            long newPositionMs, long newSegmentExcessMs, Instant now, Instant leaseUntil) {
        if (this.state != AudioPlayState.PLAYING) {
            throw new IllegalStateException("Cannot record progress from state " + this.state);
        }
        if (newPositionMs < this.positionMs || newPositionMs > this.durationMs) {
            throw new IllegalArgumentException("positionMs out of bounds: " + newPositionMs);
        }
        if (newSegmentExcessMs < 0) {
            throw new IllegalArgumentException("segmentExcessMs must be non-negative");
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        this.positionMs = newPositionMs;
        this.segmentExcessMs = Math.max(this.segmentExcessMs, newSegmentExcessMs);
        this.leaseUntil = leaseUntil;
        this.updatedAt = now;
        if (newPositionMs == this.durationMs) {
            markFinished(now);
        }
    }

    /** Completes an active playback normally ({@link AudioEndReason#FINISHED}). */
    public void markFinished(Instant now) {
        if (this.state != AudioPlayState.PLAYING) {
            return;
        }
        Objects.requireNonNull(now, "now must not be null");
        this.toleranceUsedMs = this.toleranceUsedMs + this.segmentExcessMs;
        this.segmentExcessMs = 0L;
        this.state = AudioPlayState.ENDED;
        this.endReason = AudioEndReason.FINISHED;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    /** Closes an in-progress playback when its owning part/attempt closes; leaves IDLE/ENDED unchanged. */
    public void markClosed(Instant now) {
        if (this.state != AudioPlayState.PLAYING) {
            return;
        }
        Objects.requireNonNull(now, "now must not be null");
        this.toleranceUsedMs = this.toleranceUsedMs + this.segmentExcessMs;
        this.segmentExcessMs = 0L;
        this.state = AudioPlayState.ENDED;
        this.endReason = AudioEndReason.CLOSED;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getPartId() {
        return partId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getPlayCount() {
        return playCount;
    }

    public UUID getPlayId() {
        return playId;
    }

    public String getLastRequestId() {
        return lastRequestId;
    }

    public AudioPlayState getState() {
        return state;
    }

    public AudioEndReason getEndReason() {
        return endReason;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public Instant getAnchorAt() {
        return anchorAt;
    }

    public long getAnchorPositionMs() {
        return anchorPositionMs;
    }

    public long getSegmentExcessMs() {
        return segmentExcessMs;
    }

    public long getToleranceUsedMs() {
        return toleranceUsedMs;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
