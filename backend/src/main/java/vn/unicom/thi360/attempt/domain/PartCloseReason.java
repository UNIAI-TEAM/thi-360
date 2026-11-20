package vn.unicom.thi360.attempt.domain;

/** Reason an attempt part transitioned to CLOSED (LT §4). */
public enum PartCloseReason {
    FINISHED,
    TIME_UP,
    CHOICE_TIMEOUT,
    ATTEMPT_SUBMITTED,
    FAULT_CLOSED
}
