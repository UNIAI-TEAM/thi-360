package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Khóa nhớ của một bài toán con theo môn (spec 5.4): các môn ({@link SectionOnPath}) cùng
 * {@code subjectCode} trên đường đã gộp, cộng số câu đã quyết của bài toán con. Trong một lần
 * kiểm tra, hai cách chọn môn cho cùng một môn với cùng số câu dùng lại kết quả — HSA Khoa học
 * giải tối đa 5 môn × 2 giá trị số câu = 10 bài toán con cho 10 cách chọn.
 *
 * @param subjectCode   mã môn của bài toán con
 * @param sections      các môn cùng {@code subjectCode} trên đường con (đã gộp, mỗi mục một
 *                      {@link SectionOnPath} với nhóm của nó); so khớp theo giá trị
 * @param resolvedCount tổng số câu mà bài toán con phải xếp
 */
public record SubproblemKey(String subjectCode, List<SectionOnPath> sections, int resolvedCount) {

    public SubproblemKey {
        Objects.requireNonNull(subjectCode, "subjectCode must not be null");
        sections = sections == null ? List.of() : List.copyOf(sections);
        if (resolvedCount < 0) {
            throw new IllegalArgumentException("resolvedCount must not be negative");
        }
    }
}
