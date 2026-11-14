package vn.unicom.thi360.identity.spi;

import java.util.List;
import java.util.UUID;

public interface StudentLearningProvider {

    StudentLearningData loadForUser(UUID userId);

    record StudentLearningData(
            boolean dataAvailable,
            List<Object> entitlements,
            List<Object> attempts,
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
