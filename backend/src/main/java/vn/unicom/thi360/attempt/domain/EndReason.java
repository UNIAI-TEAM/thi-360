package vn.unicom.thi360.attempt.domain;

/** Reason an active attempt finished (LT §4, §6.3). */
public enum EndReason {
    SUBMITTED,
    TIME_UP,
    CHOICE_TIMEOUT,
    FAULT_CLOSED
}
