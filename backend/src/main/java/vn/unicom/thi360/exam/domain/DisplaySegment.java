package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Phân đoạn hiển thị của một phần (spec H40): chỉ để vẽ tiêu đề, không tham gia bộ giải.
 * {@code fromPosition}, {@code toPosition} là số thứ tự câu trong phần, tính từ 1;
 * {@code commandCount} và {@code maxPoints} là số khai báo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplaySegment(
        String code,
        String name,
        Integer fromPosition,
        Integer toPosition,
        Integer commandCount,
        BigDecimal maxPoints) {
}
