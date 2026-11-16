package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/** Result for one tuple of subject-choice picks on a path. */
public record SelectionResult(
        List<SubjectPick> picks,
        SolveStatus status,
        SolveReason reason,
        String subjectCode,
        String groupId,
        QuotaRef failingQuota,
        List<SubjectWitness> witness) {

    public SelectionResult {
        picks = List.copyOf(picks);
        witness = List.copyOf(witness);
    }

    static SelectionResult from(List<SubjectPick> picks, PathResult result) {
        return new SelectionResult(picks, result.status(), result.reason(), result.subjectCode(),
                result.groupId(), result.failingQuota(), result.witness());
    }
}
