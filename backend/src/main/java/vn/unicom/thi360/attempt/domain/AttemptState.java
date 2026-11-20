package vn.unicom.thi360.attempt.domain;

/** Lifecycle state of an exam attempt (LT §4). */
public enum AttemptState {
    STARTING,
    IN_PROGRESS,
    GRADING,
    PENDING_REVIEW,
    SCORED,
    GRADING_ERROR,
    ABORTED
}
