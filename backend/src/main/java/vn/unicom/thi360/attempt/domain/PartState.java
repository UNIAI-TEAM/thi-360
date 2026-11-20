package vn.unicom.thi360.attempt.domain;

/** Lifecycle state of a part within an attempt (LT §4). */
public enum PartState {
    PENDING,
    AWAITING_CHOICE,
    GENERATING,
    GENERATION_FAILED,
    OPEN,
    CLOSED,
    SKIPPED
}
