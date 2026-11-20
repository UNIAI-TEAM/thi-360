package vn.unicom.thi360.attempt.domain;

/** Reason a STARTING attempt was aborted before opening (LT §5.2, BS §17.4). */
public enum AbortReason {
    GENERATION_FAILED,
    GENERATION_TIMEOUT,
    GENERATION_BUSY,
    STALE_STARTING,
    AUDIO_ASSET_MISSING,
    AUDIO_ASSET_INVALID
}
