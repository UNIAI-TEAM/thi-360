package vn.unicom.thi360.attempt.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when opening a new play with a fresh {@code requestId} while another play for the same
 * asset is still {@code PLAYING}, carrying that active {@code playId} so the client can resume
 * instead of consuming a new play (BS §17.2, AC-2).
 */
public final class AudioPlayInProgressException extends RuntimeException {

    private final UUID playId;

    public AudioPlayInProgressException(UUID playId) {
        super("AUDIO_PLAY_IN_PROGRESS");
        this.playId = Objects.requireNonNull(playId, "playId must not be null");
    }

    public UUID getPlayId() {
        return playId;
    }
}
