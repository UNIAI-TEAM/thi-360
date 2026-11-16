package vn.unicom.thi360.exam.domain.solver;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Bộ giải sinh đề của module exam (spec 5.4, B9, D15): dùng chung cho kiểm tra cấu hình, xem trước
 * và sinh đề.
 *
 * <p>Đầu vào là một đường lựa chọn dưới dạng danh sách <b>môn</b> ({@link SectionOnPath}: chỉ tiêu
 * cấp môn và các nhóm của nó), không phải từng nhóm riêng. Phương án {@link SubjectChoice} được mở
 * thành từng cách chọn môn ({@link SubjectPick}); mỗi cách tính một đường cho giới hạn 200 đường.
 * Đường được tách thành các bài toán con theo mã môn (gộp các môn cùng {@code subjectCode}), kết
 * quả nhớ theo {@link SubproblemKey} trong một lời gọi.
 *
 * <p>Quy tắc đếm của B9: một đơn vị đếm tính một lần vào tổng và đóng góp cùng lúc vào mọi chỉ
 * tiêu của nhóm và của môn; chùm lấy nguyên, câu con trừ vào từng chỉ tiêu theo phân loại của nó;
 * một câu, một phiên bản, một câu con chỉ dùng một lần trên cả đường.
 *
 * <p>Thuật toán (giai đoạn 2): mạng luồng chính xác hai lớp (phía nhóm → câu sức chứa 1 → phía
 * môn) cho trường hợp spec 5.4 cho phép; quay lui có cắt nhánh trên lớp đóng góp cho phần còn lại.
 * Thứ tự duyệt cố định ở chế độ {@link SolveMode.Check}; xáo theo hạt giống ở
 * {@link SolveMode.Generate}.
 *
 * <p>Thuần Java: không Spring, không DB. Hạn thời gian tính cho cả lời gọi (đọc ứng viên, liệt kê
 * đường, giải) và được kiểm qua {@link Clock} truyền vào để test được.
 */
public final class PaperSolver {

    private final SolverLimits limits;
    private final Clock clock;
    private final SubjectCountAllocator allocator;
    private final QuotaApportioner apportioner;

    /** Bộ giải với giới hạn mặc định của spec (200 đường, 20.000 ứng viên/nhóm, 30 giây, 8 môn). */
    public PaperSolver() {
        this(SolverLimits.defaults(), Clock.systemUTC(), new StructureOrderAllocator(),
                new LargestRemainderApportioner());
    }

    /** Bộ giải với giới hạn và đồng hồ cho trước (test dùng đồng hồ điều khiển được). */
    public PaperSolver(SolverLimits limits, Clock clock) {
        this(limits, clock, new StructureOrderAllocator(), new LargestRemainderApportioner());
    }

    /**
     * Bộ giải với quy tắc tạm thay thế được (spec 12): gán số câu cho môn được chọn (D25) và quy
     * chỉ tiêu tỉ lệ ra số câu (PBI-04 7.3).
     */
    public PaperSolver(SolverLimits limits, Clock clock, SubjectCountAllocator allocator,
            QuotaApportioner apportioner) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.allocator = Objects.requireNonNull(allocator, "allocator must not be null");
        this.apportioner = Objects.requireNonNull(apportioner, "apportioner must not be null");
    }

    /** Giới hạn đang dùng. */
    public SolverLimits limits() {
        return limits;
    }

    /** Quy tắc tạm gán số câu cho môn được chọn (D25). */
    public SubjectCountAllocator allocator() {
        return allocator;
    }

    /** Quy tắc tạm quy chỉ tiêu tỉ lệ ra số câu (PBI-04 7.3). */
    public QuotaApportioner apportioner() {
        return apportioner;
    }

    /**
     * Giải mọi đường lựa chọn của cấu trúc (liệt kê kiểu lặp; mỗi cách chọn môn của một
     * {@link SubjectChoice} tính một đường; tối đa {@code limits.maxPaths}).
     *
     * @param parts   các phần của cấu trúc (FIXED + CHOICE), theo thứ tự cấu trúc
     * @param source  nguồn ứng viên theo nhóm (gọi một lần mỗi giá trị {@link GroupQuota})
     * @param mode    CHECK (thứ tự cố định) hoặc GENERATE (xáo theo hạt giống)
     * @return kết quả tổng hợp và kết quả từng đường
     */
    public SolveOutcome solveAll(List<SolvePart> parts, CandidateSource source, SolveMode mode) {
        Objects.requireNonNull(parts, "parts must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        throw new UnsupportedOperationException("stage 2");
    }

    /**
     * Giải một đường đã dựng sẵn (ví dụ một đường được lấy từ phép liệt kê của solveAll, hoặc dựng
     * tay cho test).
     *
     * @param path   đường cần giải (các môn có số câu đã quyết + các cách chọn môn)
     * @param source nguồn ứng viên theo nhóm
     * @param mode   CHECK hoặc GENERATE
     * @return kết quả của đường
     */
    public PathResult solvePath(SolvePath path, CandidateSource source, SolveMode mode) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        throw new UnsupportedOperationException("stage 2");
    }

    /**
     * Chế độ xem trước (preview): giải đúng một cách chọn môn của một {@link SubjectChoice} —
     * các môn trong {@code subjectCodes} (phải là tập con đúng {@code choice.choose} môn, số câu
     * gán theo {@link SubjectCountAllocator} theo thứ tự cấu trúc) — với hạt giống
     * {@code seed}: cùng hạt giống và cùng đầu vào cho cùng một đề (spec 5.4, D25).
     *
     * @param choice       phương án SUBJECT_CHOICE đang xem trước
     * @param subjectCodes mã các môn học viên chọn (thứ tự bấm chọn không ảnh hưởng)
     * @param seed         hạt giống xáo trộn của chế độ GENERATE
     * @param source       nguồn ứng viên theo nhóm
     * @return kết quả của đường con tương ứng cách chọn này
     */
    public PathResult solvePick(SubjectChoice choice, List<String> subjectCodes, long seed,
            CandidateSource source) {
        Objects.requireNonNull(choice, "choice must not be null");
        Objects.requireNonNull(subjectCodes, "subjectCodes must not be null");
        Objects.requireNonNull(source, "source must not be null");
        throw new UnsupportedOperationException("stage 2");
    }
}
