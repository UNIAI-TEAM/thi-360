package vn.unicom.thi360.exam.domain.solver;

import java.util.Objects;

/**
 * Chỉ tiêu theo một mã năng lực (V-ACT). Đơn vị đếm gắn mã năng lực này thì cộng một vào chỉ tiêu.
 *
 * @param competencyCode mã năng lực
 * @param quota          số đơn vị đếm phải gắn mã năng lực này
 */
public record CompetencyQuota(String competencyCode, int quota) {

    public CompetencyQuota {
        Objects.requireNonNull(competencyCode, "competencyCode must not be null");
        if (quota < 0) {
            throw new IllegalArgumentException("quota must not be negative");
        }
    }
}
