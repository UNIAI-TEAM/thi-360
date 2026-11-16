package vn.unicom.thi360.exam.domain.solver;

/**
 * Mã lý do khi một đường hoặc cả yêu cầu là INVALID / UNCONFIRMED (spec 5.4).
 */
public enum SolveReason {
    /** Số đường lựa chọn vượt {@code SolverLimits.maxPaths}. */
    PATH_LIMIT_EXCEEDED,
    /** Một khối có số ứng viên vượt {@code SolverLimits.maxCandidatesPerBlock}. */
    CANDIDATE_LIMIT_EXCEEDED,
    /** Nguồn thiếu rõ ràng: chỉ tiêu không khớp số câu, hoặc nguồn của một ô/ một khối nhỏ hơn chỉ tiêu. */
    INSUFFICIENT_SOURCE,
    /** Đã duyệt hết tổ hợp: từng khối đủ nguồn nhưng không xếp được đồng thời trên cả đường. */
    NO_JOINT_SOLUTION,
    /** Vượt hạn thời gian {@code SolverLimits.timeout} trong khi giải. */
    DEADLINE_EXCEEDED
}
