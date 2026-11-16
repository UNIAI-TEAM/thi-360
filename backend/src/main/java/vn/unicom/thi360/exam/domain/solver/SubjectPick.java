package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Một cách chọn môn của một {@link SubjectChoice}: {@code choose} môn theo thứ tự cấu trúc, với số
 * câu đã được {@link SubjectCountAllocator} gán cho từng môn ({@code counts} song song
 * {@code sections}). Mỗi cách chọn là một đường con cho giới hạn 200 đường (spec 5.4) và là đầu
 * vào của chế độ xem trước ({@link PaperSolver#solvePick}).
 *
 * @param optionId id của phương án {@link SubjectChoice} sinh ra cách chọn này
 * @param sections các môn được chọn theo thứ tự cấu trúc, vẫn mang {@code questionCount = null}
 * @param counts   số câu đã gán, song song {@code sections}
 */
public record SubjectPick(String optionId, List<SectionOnPath> sections, List<Integer> counts) {

    public SubjectPick {
        Objects.requireNonNull(optionId, "optionId must not be null");
        sections = sections == null ? List.of() : List.copyOf(sections);
        counts = counts == null ? List.of() : List.copyOf(counts);
        if (sections.size() != counts.size()) {
            throw new IllegalArgumentException("counts must be parallel to sections");
        }
        for (int count : counts) {
            if (count <= 0) {
                throw new IllegalArgumentException("counts values must be positive");
            }
        }
    }

    SubjectPick(String optionId, List<SectionOnPath> sections, int[] counts) {
        this(optionId, sections, toList(counts));
    }

    private static List<Integer> toList(int[] counts) {
        List<Integer> out = new ArrayList<>(counts.length);
        for (int c : counts) {
            out.add(c);
        }
        return out;
    }

    /** Mã các môn được chọn, theo thứ tự cấu trúc — định danh của cách chọn trong kết quả. */
    public List<String> subjectCodes() {
        return sections.stream().map(SectionOnPath::subjectCode).toList();
    }

    /** Tổng số câu của cách chọn. */
    public int totalCount() {
        int sum = 0;
        for (int c : counts) {
            sum += c;
        }
        return sum;
    }

    /** Các môn của cách chọn với {@code questionCount} đã gán, theo thứ tự cấu trúc. */
    public List<SectionOnPath> resolvedSections() {
        List<SectionOnPath> out = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            out.add(sections.get(i).withQuestionCount(counts.get(i)));
        }
        return out;
    }
}
