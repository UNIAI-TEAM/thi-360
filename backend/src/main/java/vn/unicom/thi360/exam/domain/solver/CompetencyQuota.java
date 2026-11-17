package vn.unicom.thi360.exam.domain.solver;

import java.util.Objects;

/**
 * Chỉ tiêu theo một mã năng lực (V-ACT). Đơn vị đếm gắn mã năng lực này thì cộng một vào chỉ tiêu.
 *
 * @param competencyCode mã năng lực
 * @param quota          số đơn vị đếm, hoặc phần trăm khi {@code unit} là PERCENT
 * @param unit           đơn vị chỉ tiêu; các năng lực của cùng nhóm dùng cùng đơn vị
 */
public record CompetencyQuota(String competencyCode, int quota, QuotaUnit unit) {

    public CompetencyQuota {
        Objects.requireNonNull(competencyCode, "competencyCode must not be null");
        if (quota < 0) {
            throw new IllegalArgumentException("quota must not be negative");
        }
        if (unit == null) {
            unit = QuotaUnit.COUNT;
        }
        if (unit == QuotaUnit.PERCENT && quota > 100) {
            throw new IllegalArgumentException("competency percentage must not exceed 100");
        }
    }

    /** Chỉ tiêu năng lực khai theo số câu. */
    public CompetencyQuota(String competencyCode, int quota) {
        this(competencyCode, quota, QuotaUnit.COUNT);
    }
}
