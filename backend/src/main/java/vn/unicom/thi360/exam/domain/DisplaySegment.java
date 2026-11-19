package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Phân đoạn hiển thị của một phần (spec H40): chỉ để vẽ tiêu đề, không tham gia bộ giải.
 * {@code fromPosition}, {@code toPosition} là số thứ tự câu trong phần, tính từ 1;
 * {@code commandCount} và {@code maxPoints} là số khai báo. Mọi thành viên bắt buộc khi phần có phân đoạn;
 * {@code name} là tiêu đề hiển thị nên không được trống.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplaySegment(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Tiêu đề hiển thị, không trống", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer fromPosition,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer toPosition,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer commandCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal maxPoints) {
}
