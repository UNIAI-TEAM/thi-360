package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/** Quy tắc phân bổ số câu cho một bộ môn được chọn. */
public interface SubjectCountAllocator {
    /** Số câu cần giải để kiểm tra từng môn, song song với các section đã chọn. */
    List<int[]> allocations(SubjectChoice choice, List<SectionOnPath> picked);

    /** Chọn số câu cuối cùng trên đề theo hạt giống khi sinh đề. */
    default int[] select(SubjectChoice choice, List<SectionOnPath> picked, long seed) {
        List<int[]> allocations = allocations(choice, picked);
        return allocations.get(new java.util.Random(seed).nextInt(allocations.size()));
    }
}
