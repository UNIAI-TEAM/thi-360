package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Cài đặt tạm mặc định của {@link SubjectCountAllocator} theo D25 / spec 12: các môn được chọn xếp
 * theo thứ tự môn trong cấu trúc, môn thứ i trong cách chọn nhận {@code countsByPick[i]} — không
 * theo thứ tự học viên bấm chọn. Ví dụ HSA Khoa học 17–17–16: môn đứng sau cùng trong 3 môn đã
 * chọn (theo thứ tự cấu trúc) nhận 16.
 */
public final class StructureOrderAllocator implements SubjectCountAllocator {

    @Override
    public int[] allocate(SubjectChoice choice, List<String> pickedSubjectCodes) {
        Objects.requireNonNull(choice, "choice must not be null");
        Objects.requireNonNull(pickedSubjectCodes, "pickedSubjectCodes must not be null");
        int n = pickedSubjectCodes.size();
        List<Integer> counts = choice.countsByPick();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i < counts.size() ? counts.get(i) : 0;
        }
        return out;
    }
}
