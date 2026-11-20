package vn.unicom.thi360.attempt.application;

import java.util.UUID;

/** Outbox payload for topic {@code attempt.finished}, carrying strictly {@code attemptId} (plan AT-2a). */
public record AttemptFinishedEvent(UUID attemptId) {}
