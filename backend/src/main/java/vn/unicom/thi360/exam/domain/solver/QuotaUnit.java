package vn.unicom.thi360.exam.domain.solver;

/**
 * Đơn vị của một giá trị chỉ tiêu (mock {@code MatrixUnit}, spec 5.2 B9).
 *
 * <p>{@link #COUNT}: giá trị là số câu nguyên. {@link #PERCENT}: giá trị là phần trăm, được quy ra
 * số câu bằng phần dư lớn nhất ({@link QuotaApportioner}, PBI-04 7.3) trước khi giải. Chỉ tiêu theo
 * tỉ lệ bắt buộc khi số câu của môn thay đổi theo cách chọn môn (HSA Khoa học 16/17, spec 12).
 */
public enum QuotaUnit {
    /** Giá trị là số đơn vị đếm nguyên. */
    COUNT,
    /** Giá trị là phần trăm (thang 100); quy ra số câu bằng phần dư lớn nhất. */
    PERCENT
}
