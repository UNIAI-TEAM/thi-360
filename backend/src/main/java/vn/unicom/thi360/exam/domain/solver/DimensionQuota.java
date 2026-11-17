package vn.unicom.thi360.exam.domain.solver;

import java.util.Objects;

/**
 * Chỉ tiêu theo một giá trị của một chiều riêng trong ma trận SEPARATE (theo khối lớp hoặc theo độ khó).
 *
 * @param code  mã giá trị của chiều (mã khối lớp hoặc mã độ khó)
 * @param quota số đơn vị đếm phải khớp đúng giá trị này
 */
public record DimensionQuota(String code, int quota) {

    public DimensionQuota {
        Objects.requireNonNull(code, "code must not be null");
        if (quota < 0) {
            throw new IllegalArgumentException("quota must not be negative");
        }
    }
}
