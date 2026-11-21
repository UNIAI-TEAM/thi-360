package vn.unicom.thi360.attempt.domain;

/** Terminal reason why an active audio play transitioned to {@link AudioPlayState#ENDED} (BS §17.2). */
public enum AudioEndReason {
    FINISHED,
    CLOSED
}
