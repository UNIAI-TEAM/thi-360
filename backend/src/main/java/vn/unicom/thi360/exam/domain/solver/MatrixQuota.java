package vn.unicom.thi360.exam.domain.solver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ma trận chỉ tiêu lớp × độ khó, dùng chung cho chỉ tiêu cấp môn ({@link SubjectQuota#matrix()})
 * và chỉ tiêu riêng của nhóm ({@link GroupQuota#matrix()}) — mock {@code BlockMatrix}, spec 5.2 B9.
 *
 * <p>{@link Combined}: chỉ tiêu theo từng ô Lớp × Độ khó; một đơn vị đếm chỉ khớp đúng một ô.
 * {@link Separate}: hai chiều riêng; một đơn vị khớp một chỉ tiêu khối lớp và một chỉ tiêu độ khó
 * cùng lúc, nên tổng mỗi chiều phải bằng số đơn vị được kiểm soát.
 *
 * <p>{@link #unit()} cho biết giá trị là số câu ({@link QuotaUnit#COUNT}) hay phần trăm
 * ({@link QuotaUnit#PERCENT}, quy bằng {@link QuotaApportioner}). Ô / giá trị không khai báo được
 * coi là chỉ tiêu 0: đơn vị khớp vào đó không thể được chọn.
 */
public sealed interface MatrixQuota {

    /** Đơn vị của mọi giá trị chỉ tiêu trong ma trận; mặc định {@link QuotaUnit#COUNT}. */
    QuotaUnit unit();

    /** Chỉ tiêu theo ô Lớp × Độ khó. */
    record Combined(List<CellQuota> cells, QuotaUnit unit) implements MatrixQuota {

        public Combined {
            cells = cells == null ? List.of() : List.copyOf(cells);
            if (unit == null) {
                unit = QuotaUnit.COUNT;
            }
            Set<String> seen = new HashSet<>();
            for (CellQuota cell : cells) {
                if (!seen.add(cell.gradeCode() + " " + cell.difficultyCode())) {
                    throw new IllegalArgumentException(
                            "duplicate cell " + cell.gradeCode() + "/" + cell.difficultyCode());
                }
            }
        }

        /** Ma trận ô với đơn vị mặc định COUNT. */
        public Combined(List<CellQuota> cells) {
            this(cells, QuotaUnit.COUNT);
        }
    }

    /** Chỉ tiêu hai chiều riêng: khối lớp và độ khó. */
    record Separate(List<DimensionQuota> grades, List<DimensionQuota> difficulties, QuotaUnit unit)
            implements MatrixQuota {

        public Separate {
            grades = grades == null ? List.of() : List.copyOf(grades);
            difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
            if (unit == null) {
                unit = QuotaUnit.COUNT;
            }
            rejectDuplicateCodes(grades);
            rejectDuplicateCodes(difficulties);
        }

        /** Ma trận hai chiều với đơn vị mặc định COUNT. */
        public Separate(List<DimensionQuota> grades, List<DimensionQuota> difficulties) {
            this(grades, difficulties, QuotaUnit.COUNT);
        }

        private static void rejectDuplicateCodes(List<DimensionQuota> quotas) {
            Set<String> seen = new HashSet<>();
            for (DimensionQuota quota : quotas) {
                if (!seen.add(quota.code())) {
                    throw new IllegalArgumentException("duplicate dimension code " + quota.code());
                }
            }
        }
    }
}
