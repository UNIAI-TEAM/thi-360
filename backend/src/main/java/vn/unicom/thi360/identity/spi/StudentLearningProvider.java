package vn.unicom.thi360.identity.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StudentLearningProvider {

    StudentLearningData loadForUser(UUID userId);

    enum EntitlementLearningStatus {
        ACTIVE,
        IN_USE,
        EXHAUSTED,
        REVOKED
    }

    enum EntitlementLearningSource {
        PURCHASE,
        CLASS
    }

    enum AttemptLearningStatus {
        IN_PROGRESS,
        GRADING,
        PENDING_REVIEW,
        SCORED,
        GRADING_ERROR
    }

    record EntitlementItem(
            UUID entitlementId,
            UUID examId,
            String examCode,
            String examName,
            EntitlementLearningSource source,
            int attemptsTotal,
            int attemptsUsed,
            int attemptsReserved,
            int attemptsRevoked,
            int attemptsLeft,
            EntitlementLearningStatus status
    ) {
    }

    record AttemptItem(
            UUID attemptId,
            UUID examId,
            String examName,
            String examKindCode,
            String scope,
            Integer attemptNo,
            AttemptLearningStatus status,
            Instant startedAt,
            Instant finishedAt,
            BigDecimal score,
            BigDecimal maxScore
    ) {
    }

    record StudentLearningData(
            boolean dataAvailable,
            List<EntitlementItem> entitlements,
            List<AttemptItem> attempts,
            List<Object> assessments,
            List<Object> practice,
            List<Object> supportRequests
    ) {
        public StudentLearningData {
            entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            assessments = assessments == null ? List.of() : List.copyOf(assessments);
            practice = practice == null ? List.of() : List.copyOf(practice);
            supportRequests = supportRequests == null ? List.of() : List.copyOf(supportRequests);
        }

        public static StudentLearningData empty() {
            return new StudentLearningData(false, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
