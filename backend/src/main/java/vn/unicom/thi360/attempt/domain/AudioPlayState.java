package vn.unicom.thi360.attempt.domain;

/** Lifecycle state of a single audio asset within an attempt (BS §17.2). */
public enum AudioPlayState {
    IDLE,
    PLAYING,
    ENDED
}
