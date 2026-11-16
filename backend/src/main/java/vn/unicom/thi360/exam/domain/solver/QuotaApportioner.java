package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Quy tắc tạm (spec 12) quy chỉ tiêu theo tỉ lệ ({@link QuotaUnit#PERCENT}) ra số câu nguyên
 * (PBI-04 7.3). Cài đặt mặc định {@link LargestRemainderApportioner} dùng phần dư lớn nhất và phải
 * khớp quy tắc hòa của web ({@code apps/web-admin/src/features/exams/matrix.ts},
 * {@code apportionByPercent}): phần còn thiếu đi tới mục có phần thập phân lớn hơn, hòa thì theo
 * thứ tự khai báo.
 */
@FunctionalInterface
public interface QuotaApportioner {

    /**
     * Quy dãy giá trị tỉ lệ ra số câu nguyên.
     *
     * @param percents giá trị tỉ lệ theo thứ tự khai báo (thang 100; tổng có thể khác 100)
     * @param total    tổng số câu cần phân
     * @return số câu nguyên song song {@code percents}
     */
    int[] apportion(List<Integer> percents, int total);
}
