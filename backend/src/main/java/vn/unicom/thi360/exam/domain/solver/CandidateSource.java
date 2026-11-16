package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Nguồn ứng viên của một nhóm: bản chiếu gọn từ {@code findCandidates} (spec 3), đã được lọc theo
 * phạm vi nguồn của nhóm — dạng bài, dạng trả lời, MAPID có hiệu lực (của nhóm, hoặc của môn khi
 * nhóm kế thừa — D21/D23), nhãn, năng lực, điểm yêu cầu. Bộ giải nhận nguồn qua cổng này, không
 * tự lọc theo phạm vi.
 *
 * <p>Bộ giải gọi nguồn một lần cho mỗi giá trị {@link GroupQuota} khác nhau và nhớ kết quả theo
 * toàn bộ giá trị của {@link GroupQuota} (so theo giá trị record), không theo riêng
 * {@code groupId}.
 */
@FunctionalInterface
public interface CandidateSource {

    /**
     * Ứng viên của một nhóm, theo thứ tự nguồn cung cấp.
     *
     * @param group nhóm cần nguồn
     * @return danh sách ứng viên (không null); vượt {@code SolverLimits.maxCandidatesPerBlock}
     *         thì đường kết quả là UNCONFIRMED/CANDIDATE_LIMIT_EXCEEDED
     */
    List<PathCandidate> candidatesFor(GroupQuota group);
}
