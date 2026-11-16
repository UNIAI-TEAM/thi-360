package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Một đường lựa chọn đã mở đầy đủ (spec 5.4): danh sách có thứ tự các môn trên đường
 * ({@link SectionOnPath}, đã gán {@code questionCount} với cả môn đến từ {@link SubjectChoice}),
 * cộng các phương án {@link SubjectChoice} được chọn ({@code choices}) và các cách chọn môn của
 * chúng ({@code picks}) còn phải giải.
 *
 * <p>Bộ giải không dựng sẵn danh sách đường: các đường được liệt kê lần lượt theo kiểu lặp,
 * {@code pathIndex} là chỉ số của đường trong phép liệt kê (mỗi cách chọn môn tính một đường).
 *
 * @param pathIndex chỉ số đường trong phép liệt kê (từ 0)
 * @param chosen    phương án được chọn của từng phần tự chọn, theo thứ tự phần (nhãn đường)
 * @param sections  các môn trên đường với số câu đã biết (không chứa môn của {@code picks})
 * @param choices   các phương án SUBJECT_CHOICE được chọn trên đường này
 * @param picks     cách chọn môn của từng phần tử {@code choices}; rỗng với mỗi phần tử nghĩa là
 *                  đường chưa được mở (dạng đầu vào của solveAll), mỗi tổ hợp cách chọn là một
 *                  đường con riêng
 */
public record SolvePath(
        long pathIndex,
        List<ChosenOption> chosen,
        List<SectionOnPath> sections,
        List<SubjectChoice> choices,
        List<List<SubjectPick>> picks) {

    public SolvePath {
        chosen = chosen == null ? List.of() : List.copyOf(chosen);
        sections = sections == null ? List.of() : List.copyOf(sections);
        choices = choices == null ? List.of() : List.copyOf(choices);
        picks = picks == null ? List.of() : picks.stream().map(List::copyOf).toList();
        if (picks.size() > choices.size()) {
            throw new IllegalArgumentException("picks must not outnumber choices");
        }
    }

    /** Đường chỉ có môn cố định, không phương án SUBJECT_CHOICE. */
    public static SolvePath of(long pathIndex, List<SectionOnPath> sections) {
        return new SolvePath(pathIndex, List.of(), sections, List.of(), List.of());
    }
}
