package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/** A selected question and its per-counting-unit topic assignments. */
public record QuestionPick(String questionId, int versionNo, List<TopicAssignment> topicAssignments) {
    public QuestionPick {
        topicAssignments = topicAssignments == null ? List.of() : List.copyOf(topicAssignments);
    }

    public QuestionPick(String questionId, int versionNo) {
        this(questionId, versionNo, List.of());
    }
}
