package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Kết quả kiểm tra cấu hình gần nhất của bài thi")
public record ExamCheckSummary(
        @Schema(description = "UNCHECKED | VALID | INVALID | NEEDS_RECHECK",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(description = "Số vấn đề phát hiện ở lần kiểm gần nhất",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int issueCount,
        @Schema(description = "Thời điểm kiểm")
        Instant checkedAt
) {
}
