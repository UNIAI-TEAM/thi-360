package vn.unicom.thi360.exam.domain.solver;

/**
 * Thống kê của một lời gọi giải, để test kiểm chứng tính nhớ bài toán con (spec 5.4: HSA Khoa học
 * ≤ 10 bài toán con cho 10 cách chọn).
 *
 * @param pathsEnumerated    số đường đã liệt kê (mỗi cách chọn môn tính một đường)
 * @param subproblemsSolved  số bài toán con theo môn đã thực sự giải (không tính lần dùng lại)
 * @param subproblemsMemoHit số lần bài toán con được trả từ bộ nhớ ({@link SubproblemKey})
 */
public record SolveStats(long pathsEnumerated, long subproblemsSolved, long subproblemsMemoHit) {

    public static final SolveStats EMPTY = new SolveStats(0, 0, 0);

    public SolveStats {
        if (pathsEnumerated < 0 || subproblemsSolved < 0 || subproblemsMemoHit < 0) {
            throw new IllegalArgumentException("stats must not be negative");
        }
    }
}
