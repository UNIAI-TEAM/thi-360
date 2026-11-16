package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một phần của cấu trúc bài thi ở dạng đầu vào của bộ giải (mock {@code ExamPart}, spec 5.2/5.4).
 *
 * <p>{@link Fixed}: mọi học viên làm các môn trong {@code sections}. {@link Choice}: học viên làm
 * các môn chung {@code commonSections} (có thể rỗng) cộng {@code choose} phương án trong
 * {@code options}; phương án {@link SubjectChoice} mở thành từng cách chọn môn. Tích các cách
 * chọn trên các phần CHOICE tạo thành các đường lựa chọn.
 */
public sealed interface SolvePart {

    /** Phần cố định. */
    record Fixed(String partId, List<SectionOnPath> sections) implements SolvePart {

        public Fixed {
            Objects.requireNonNull(partId, "partId must not be null");
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        /** Phần cố định một môn. */
        public static Fixed of(String partId, SectionOnPath section) {
            return new Fixed(partId, List.of(section));
        }
    }

    /** Phần tự chọn: môn chung (nếu có) cộng số phương án được chọn. */
    record Choice(String partId, List<SectionOnPath> commonSections, List<SolveOption> options,
                  int choose) implements SolvePart {

        public Choice {
            Objects.requireNonNull(partId, "partId must not be null");
            commonSections = commonSections == null ? List.of() : List.copyOf(commonSections);
            options = options == null ? List.of() : List.copyOf(options);
            if (choose < 1) {
                throw new IllegalArgumentException("choose must be positive");
            }
        }


        /** Giữ cách gọi chọn một phương án. */
        public Choice(String partId, List<SectionOnPath> commonSections, List<SolveOption> options) {
            this(partId, commonSections, options, 1);
        }
        /** Phần tự chọn không có môn chung. */
        public static Choice of(String partId, List<SolveOption> options) {
            return new Choice(partId, List.of(), options);
        }

        /** Phần tự chọn không có môn chung, chọn nhiều phương án. */
        public static Choice of(String partId, List<SolveOption> options, int choose) {
            return new Choice(partId, List.of(), options, choose);
        }
    }
}
