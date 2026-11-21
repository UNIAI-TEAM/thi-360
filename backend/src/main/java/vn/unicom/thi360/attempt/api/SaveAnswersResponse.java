package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Kết quả lưu nháp câu trả lời (LT §7.1)")
public record SaveAnswersResponse(
        @Schema(description = "Kết quả xử lý từng thay đổi theo thứ tự gửi", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FinalAnswerResult> results,
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow) {

    public SaveAnswersResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
