package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Phương án {@code SUBJECT_CHOICE} của một phần tự chọn (B9, D25, spec 5.2): học viên chọn
 * {@code choose} trong số {@code sections} môn; số câu từng môn tính từ {@code countsByPick}
 * (kích thước = {@code choose}, tổng = số câu của phương án) qua {@link SubjectCountAllocator}.
 *
 * <p>Mỗi cách chọn môn ({@link SubjectPick}) tính là một đường cho giới hạn 200 đường (spec 5.4).
 * Các {@link SectionOnPath} của phương án có {@code questionCount = null} — số câu chỉ được gán
 * trong từng {@link SubjectPick}.
 *
 * @param optionId     id của phương án trong cấu trúc (để ghi vào nhãn đường và kết quả)
 * @param sections     các môn được phép chọn, 2..8 môn (giới hạn giải ở
 *                     {@link SolverLimits#maxChoiceSubjects()}), theo thứ tự cấu trúc
 * @param choose       số môn học viên phải chọn, 1..sections
 * @param countsByPick {@code choose} số câu, gán theo {@link SubjectCountAllocator}
 */
public record SubjectChoice(
        String optionId,
        List<SectionOnPath> sections,
        int choose,
        List<Integer> countsByPick) implements SolveOption {

    public SubjectChoice {
        Objects.requireNonNull(optionId, "optionId must not be null");
        sections = sections == null ? List.of() : List.copyOf(sections);
        if (sections.size() < 2) {
            throw new IllegalArgumentException("SubjectChoice needs at least 2 sections");
        }
        if (choose <= 0 || choose > sections.size()) {
            throw new IllegalArgumentException("choose must be between 1 and sections.size()");
        }
        countsByPick = countsByPick == null ? List.of() : List.copyOf(countsByPick);
        if (countsByPick.size() != choose) {
            throw new IllegalArgumentException("countsByPick must have exactly choose values");
        }
        for (int count : countsByPick) {
            if (count <= 0) {
                throw new IllegalArgumentException("countsByPick values must be positive");
            }
        }
        for (SectionOnPath section : sections) {
            if (section.questionCount() != null) {
                throw new IllegalArgumentException(
                        "sections of a SubjectChoice must have questionCount = null (resolved per pick)");
            }
        }
    }

    /** Số cách chọn môn của phương án = C(sections, choose); mỗi cách tính một đường (spec 5.4). */
    public long pickCount() {
        return binomial(sections.size(), choose);
    }

    /**
     * Mọi cách chọn {@code choose} môn, theo thứ tự tổ hợp ổn định; số câu gán bằng
     * {@code allocator}.
     */
    public List<SubjectPick> picks(SubjectCountAllocator allocator) {
        Objects.requireNonNull(allocator, "allocator must not be null");
        List<SubjectPick> out = new ArrayList<>((int) Math.min(pickCount(), Integer.MAX_VALUE));
        int n = sections.size();
        int k = choose;
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) {
            idx[i] = i;
        }
        while (idx[0] <= n - k) {
            List<SectionOnPath> picked = new ArrayList<>(k);
            for (int i : idx) {
                picked.add(sections.get(i));
            }
            out.add(new SubjectPick(optionId, picked, allocator.allocate(this,
                    picked.stream().map(SectionOnPath::subjectCode).toList())));
            // tổ hợp kế tiếp theo thứ tự từ điển
            int p = k - 1;
            while (p >= 0 && idx[p] == n - k + p) {
                p--;
            }
            if (p < 0) {
                break;
            }
            idx[p]++;
            for (int j = p + 1; j < k; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
        return out;
    }

    /** C(n,k) cắt trần ở {@link Long#MAX_VALUE}. */
    private static long binomial(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        long r = 1;
        for (int i = 0; i < Math.min(k, n - k); i++) {
            if (r > Long.MAX_VALUE / (n - i)) {
                return Long.MAX_VALUE;
            }
            r = r * (n - i) / (i + 1);
        }
        return r;
    }
}
