package vn.unicom.thi360.exam.domain.solver.oracle;

import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SolvePart;

/**
 * Một ca sinh ngẫu nhiên cho {@link PaperSolverOracleTest}: cấu trúc ({@code parts}) và nguồn
 * ứng viên từng nhóm ({@code sources}, khóa theo giá trị {@link GroupQuota} như cách bộ giải nhớ
 * nguồn). Kích thước nằm trong miền của {@link BruteForceOracle}: ≤ 4 nhóm trên ≤ 3 môn,
 * ≤ 12 ứng viên mỗi nhóm, chùm 2–3 câu con, tối đa một {@code SUBJECT_CHOICE} 3 môn chọn 2.
 *
 * @param id       số thứ tự ca (để tái hiện khi lỗi)
 * @param category nhóm ca theo contract W-ORACLE
 * @param parts    cấu trúc đầu vào của {@code solveAll}
 * @param sources  ứng viên của từng nhóm
 */
public record OracleCase(
        int id,
        Category category,
        List<SolvePart> parts,
        Map<GroupQuota, List<PathCandidate>> sources) {

    /** Nhóm ca (contract: ≥ 200 ca chỉ tiêu môn trên ≥ 2 nhóm có chùm, ≥ 100 ca SUBJECT_CHOICE). */
    public enum Category {
        /** Nhóm câu đơn không chỉ tiêu cấp môn. */
        BASIC_SINGLE,
        /** Chỉ tiêu cấp môn trên các nhóm câu đơn. */
        SUBJECT_QUOTA,
        /** Nhóm câu chùm không chỉ tiêu cấp môn. */
        SETS,
        /** Chỉ tiêu cấp môn trải trên ≥ 2 nhóm, có cả chùm — bắt buộc ≥ 200 ca. */
        SUBJECT_QUOTA_SETS,
        /** Đường có SUBJECT_CHOICE — bắt buộc ≥ 100 ca. */
        SUBJECT_CHOICE,
        /** Hai nhóm tranh nhau cùng ứng viên (tính duy nhất trên đường). */
        SHARED_SOURCE
    }

    /** Nguồn ứng viên dạng {@link CandidateSource} của ca này. */
    public CandidateSource source() {
        return group -> sources.getOrDefault(group, List.of());
    }
}
