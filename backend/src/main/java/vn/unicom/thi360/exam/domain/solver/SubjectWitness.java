package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Đề chứng minh của một bài toán con theo môn (spec 5.4): đủ để dựng đề cho mọi cách chọn môn có
 * cùng {@link SubproblemKey}. Một đường VALID mang một {@link SubjectWitness} cho mỗi bài toán
 * con của nó.
 *
 * @param subjectCode   mã môn của bài toán con
 * @param resolvedCount số câu đã xếp của bài toán con
 * @param groups        đề chứng minh từng nhóm (song song các nhóm của các môn trong bài toán con)
 */
public record SubjectWitness(String subjectCode, int resolvedCount, List<GroupWitness> groups) {

    public SubjectWitness {
        Objects.requireNonNull(subjectCode, "subjectCode must not be null");
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
