package vn.unicom.thi360.attempt.application;

/**
 * Thrown when a progress report violates backward/bounds/anti-seek limits, carrying the saved
 * {@code positionMs} so the player snaps back cleanly without altering server state (BS §17.2, AC-6).
 */
public final class AudioSeekRejectedException extends RuntimeException {

    private final long positionMs;

    public AudioSeekRejectedException(long positionMs) {
        super("AUDIO_SEEK_REJECTED");
        this.positionMs = positionMs;
    }

    public long getPositionMs() {
        return positionMs;
    }
}
