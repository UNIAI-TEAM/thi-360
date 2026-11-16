package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một phương án của phần tự chọn (mock {@code ExamPartOption}, spec 5.2): hoặc một nhóm môn cố
 * định ({@link Sections} — một môn, một mã tổ hợp, một nhánh nội dung), hoặc một
 * {@link SubjectChoice} (HSA Khoa học — mở thành từng cách chọn môn, mỗi cách tính một đường).
 */
public sealed interface SolveOption permits SolveOption.Sections, SubjectChoice {

    /** Phương án thường: học viên làm đúng các môn trong {@code sections}. */
    record Sections(String optionId, List<SectionOnPath> sections) implements SolveOption {

        public Sections {
            Objects.requireNonNull(optionId, "optionId must not be null");
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        /** Một phương án một môn. */
        public static Sections of(String optionId, SectionOnPath section) {
            return new Sections(optionId, List.of(section));
        }
    }
}
