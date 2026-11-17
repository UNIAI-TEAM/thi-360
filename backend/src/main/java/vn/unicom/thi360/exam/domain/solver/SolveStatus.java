package vn.unicom.thi360.exam.domain.solver;

/**
 * Trạng thái giải của một đường lựa chọn (spec 5.4).
 */
public enum SolveStatus {
    /** Bộ giải tìm được đề thỏa mọi ràng buộc, kèm đề chứng minh. */
    VALID,
    /** Bộ giải đã duyệt hết không gian chọn, không có đề nào thỏa. */
    INVALID,
    /** Không kết luận được: hết hạn thời gian hoặc vượt giới hạn đầu vào. */
    UNCONFIRMED
}
