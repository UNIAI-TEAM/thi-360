package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Quy tắc tạm (spec 12, D25) gán số câu cho từng môn được chọn của một {@link SubjectChoice}:
 * với một cách chọn môn (đã lọc còn {@code choose} môn, theo thứ tự cấu trúc), trả mảng số câu
 * song song với {@code pickedSubjectCodes}.
 *
 * <p>Cách làm tạm chờ BA chốt nằm sau interface này để đổi bằng một thay thế nhỏ; cài đặt mặc định
 * là {@link StructureOrderAllocator}.
 */
@FunctionalInterface
public interface SubjectCountAllocator {

    /**
     * Số câu của từng môn được chọn.
     *
     * @param choice             phương án SUBJECT_CHOICE đang mở
     * @param pickedSubjectCodes mã các môn được chọn, theo thứ tự cấu trúc (đúng {@code choice.choose} phần tử)
     * @return mảng số câu song song {@code pickedSubjectCodes}; mỗi phần tử &gt; 0
     */
    int[] allocate(SubjectChoice choice, List<String> pickedSubjectCodes);
}
