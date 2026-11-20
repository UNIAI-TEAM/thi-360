package vn.unicom.thi360.attempt.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;

/** Attempt entry serialized into {@code StudentLearningResponse.attempts} (AC-32, LT §11). */
public record AttemptLearningRow(
        UUID attemptId,
        UUID examId,
        String examName,
        String examKindCode,
        String scope,
        Integer attemptNo,
        AttemptState state,
        Instant startedAt,
        Instant finishedAt,
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal maxScore) {
}
