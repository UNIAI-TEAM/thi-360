package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Kết quả giải của một đường lựa chọn (spec 5.4).
 *
 * @param path    đường đã giải
 * @param status  VALID / INVALID / UNCONFIRMED
 * @param reason  mã lý do khi INVALID hoặc UNCONFIRMED; null khi VALID
 * @param blockId id khối liên quan tới lý do (khối thiếu nguồn, khối vượt giới hạn ứng viên); null nếu không áp dụng
 * @param witness đề chứng minh khi VALID (một phần tử cho mỗi khối); rỗng khi INVALID / UNCONFIRMED
 */
public record PathResult(SolvePath path, SolveStatus status, SolveReason reason, String blockId,
        List<BlockWitness> witness) {

    public PathResult {
        witness = witness == null ? List.of() : List.copyOf(witness);
    }

    static PathResult valid(SolvePath path, List<BlockWitness> witness) {
        return new PathResult(path, SolveStatus.VALID, null, null, witness);
    }

    static PathResult invalid(SolvePath path, SolveReason reason, String blockId) {
        return new PathResult(path, SolveStatus.INVALID, reason, blockId, List.of());
    }

    static PathResult unconfirmed(SolvePath path, SolveReason reason, String blockId) {
        return new PathResult(path, SolveStatus.UNCONFIRMED, reason, blockId, List.of());
    }
}
