package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Kết quả giải của một đường lựa chọn (spec 5.4).
 *
 * <p>Đường có {@link SubjectChoice}: VALID khi mọi cách chọn môn VALID; INVALID khi có ít nhất một
 * cách INVALID (khi đó {@code failingSelections} chỉ đúng các cách chọn lỗi, nêu môn và số câu của
 * từng trường hợp); còn lại UNCONFIRMED.
 *
 * @param path              đường đã giải
 * @param status            VALID / INVALID / UNCONFIRMED
 * @param reason            mã lý do khi INVALID hoặc UNCONFIRMED; null khi VALID
 * @param subjectCode       mã môn liên quan tới lý do; null nếu không áp dụng
 * @param groupId           id nhóm liên quan tới lý do (nhóm thiếu nguồn, nhóm vượt giới hạn ứng viên); null nếu không áp dụng
 * @param witness           đề chứng minh theo bài toán con khi VALID (một {@link SubjectWitness} cho mỗi
 *                          (môn, số câu) trên đường); rỗng khi INVALID / UNCONFIRMED
 * @param failingSelections các cách chọn môn ({@link SubjectPick}) không giải được của một đường có
 *                          {@link SubjectChoice}; chỉ khác rỗng khi INVALID do cách chọn lỗi
 * @param failingQuota      chỉ tiêu cấp môn thiếu nguồn, hoặc null khi không xác định được
 * @param selections        kết quả riêng cho từng tổ hợp chọn môn đã được giải
 * @param droppedQuestion   câu đơn đã bỏ khi sinh đề, hoặc null nếu không cắt
 */
public record PathResult(
        SolvePath path,
        SolveStatus status,
        SolveReason reason,
        String subjectCode,
        String groupId,
        List<SubjectWitness> witness,
        List<SubjectPick> failingSelections,
        QuotaRef failingQuota,
        List<SelectionResult> selections,
        QuestionPick droppedQuestion) {

    public PathResult(SolvePath path, SolveStatus status, SolveReason reason, String subjectCode,
            String groupId, List<SubjectWitness> witness, List<SubjectPick> failingSelections,
            QuotaRef failingQuota, List<SelectionResult> selections) {
        this(path, status, reason, subjectCode, groupId, witness, failingSelections, failingQuota,
                selections, null);
    }

    public PathResult(SolvePath path, SolveStatus status, SolveReason reason, String subjectCode,
            String groupId, List<SubjectWitness> witness, List<SubjectPick> failingSelections,
            QuotaRef failingQuota) {
        this(path, status, reason, subjectCode, groupId, witness, failingSelections, failingQuota,
                List.of());
    }

    public PathResult {
        witness = witness == null ? List.of() : List.copyOf(witness);
        failingSelections = failingSelections == null ? List.of() : List.copyOf(failingSelections);
        selections = selections == null ? List.of() : List.copyOf(selections);
    }

    PathResult withSelections(List<SelectionResult> values) {
        return new PathResult(path, status, reason, subjectCode, groupId, witness, failingSelections,
                failingQuota, values, droppedQuestion);
    }

    static PathResult valid(SolvePath path, List<SubjectWitness> witness) {
        return new PathResult(path, SolveStatus.VALID, null, null, null, witness, List.of(), null);
    }

    static PathResult invalid(SolvePath path, SolveReason reason, String subjectCode, String groupId) {
        return invalid(path, reason, subjectCode, groupId, null);
    }

    static PathResult invalid(SolvePath path, SolveReason reason, String subjectCode,
            String groupId, QuotaRef failingQuota) {
        return new PathResult(path, SolveStatus.INVALID, reason, subjectCode, groupId, List.of(),
                List.of(), failingQuota);
    }

    static PathResult invalidPicks(SolvePath path, SolveReason reason, String subjectCode,
            String groupId, List<SubjectPick> failingSelections, QuotaRef failingQuota) {
        return new PathResult(path, SolveStatus.INVALID, reason, subjectCode, groupId, List.of(),
                failingSelections, failingQuota);
    }

    static PathResult unconfirmed(SolvePath path, SolveReason reason, String subjectCode,
            String groupId) {
        return new PathResult(path, SolveStatus.UNCONFIRMED, reason, subjectCode, groupId, List.of(),
                List.of(), null);
    }
}
