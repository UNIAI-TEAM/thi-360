package vn.unicom.thi360.exam.domain.solver;

import java.util.Objects;

/**
 * Chỉ tiêu của một ô Lớp × Độ khó trong ma trận COMBINED.
 *
 * @param gradeCode      mã khối lớp của ô
 * @param difficultyCode mã độ khó của ô
 * @param quota          số đơn vị đếm phải khớp đúng ô này
 */
public record CellQuota(String gradeCode, String difficultyCode, int quota) {

    public CellQuota {
        Objects.requireNonNull(gradeCode, "gradeCode must not be null");
        Objects.requireNonNull(difficultyCode, "difficultyCode must not be null");
        if (quota < 0) {
            throw new IllegalArgumentException("quota must not be negative");
        }
    }
}
