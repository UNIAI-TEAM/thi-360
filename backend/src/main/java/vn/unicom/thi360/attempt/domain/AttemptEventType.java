package vn.unicom.thi360.attempt.domain;

/** Operational event types recorded on {@code attempt.attempt_event} (LT §4). */
public enum AttemptEventType {
    STARTED,
    GENERATION_FAILED,
    PART_OPENED,
    PART_CLOSED,
    CHOICE_CONFIRMED,
    SESSION_CLAIMED,
    SESSION_TAKEN_OVER,
    SAVE_REJECTED,
    SUBMITTED,
    TIME_UP,
    CHOICE_TIMEOUT,
    GRADED,
    GRADING_FAILED,
    REGRADED,
    COMPENSATED
}
