package vn.unicom.thi360.exam.domain.solver;

/**
 * Mã lý do khi một đường hoặc cả yêu cầu là INVALID / UNCONFIRMED (spec 5.4).
 */
public enum SolveReason {
    /** Số đường lựa chọn vượt {@code SolverLimits.maxPaths} (mỗi cách chọn môn tính một đường). */
    PATH_LIMIT_EXCEEDED,
    /** Một nhóm có số ứng viên vượt {@code SolverLimits.maxCandidatesPerBlock}. */
    CANDIDATE_LIMIT_EXCEEDED,
    /** Một {@code SubjectChoice} có số môn vượt {@code SolverLimits.maxChoiceSubjects} (spec 5.2: 8). */
    CHOICE_SUBJECT_LIMIT_EXCEEDED,
    /** Hai nhóm trên cùng đường mang trùng {@code groupId} (R1-3). */
    DUPLICATE_GROUP_ID,
    /** Nguồn thiếu rõ ràng: chỉ tiêu không khớp số câu, hoặc nguồn của một ô/ một nhóm nhỏ hơn chỉ tiêu. */
    INSUFFICIENT_SOURCE,
    /** Chỉ tiêu cấp môn (dạng trả lời, lớp, độ khó — B9) không thể thỏa trên hợp nguồn của các nhóm của môn. */
    SUBJECT_QUOTA_INFEASIBLE,
    /** Đã duyệt hết tổ hợp: từng nhóm đủ nguồn nhưng không xếp được đồng thời trên cả đường. */
    NO_JOINT_SOLUTION,
    /** Vượt hạn thời gian {@code SolverLimits.timeout} trong khi giải. */
    DEADLINE_EXCEEDED
}
