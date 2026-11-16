package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/** Quy tắc phân bổ số câu cho một bộ môn được chọn. */
public interface SubjectCountAllocator {
    /** Mọi cách phân bổ có thể xảy ra, song song với các section đã chọn. */
    List<int[]> allocations(SubjectChoice choice, List<SectionOnPath> picked);

    /** Chọn một cách phân bổ theo hạt giống khi sinh đề. */
    default int[] select(SubjectChoice choice, List<SectionOnPath> picked, long seed) {
        List<int[]> allocations = allocations(choice, picked);
        return allocations.get(new java.util.Random(seed).nextInt(allocations.size()));
    }
}
