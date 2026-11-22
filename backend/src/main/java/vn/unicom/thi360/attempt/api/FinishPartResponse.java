package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;

@Schema(description = "Kết quả hoàn thành phần và trạng thái phần kế tiếp (LT §6.3)")
public record FinishPartResponse(
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow,
        @Schema(description = "Id lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID attemptId,
        @Schema(description = "Trạng thái hiện tại của lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptState attemptState,
        @Schema(description = "Lý do kết thúc lượt nếu lượt đã đóng", nullable = true)
        EndReason endReason,
        @Schema(description = "Thời điểm kết thúc lượt nếu lượt đã đóng", nullable = true)
        Instant finishedAt,
        @Schema(description = "Thông tin phần đã đóng", requiredMode = Schema.RequiredMode.REQUIRED)
        PartSummaryView closedPart,
        @Schema(description = "Thông tin phần kế tiếp nếu có", nullable = true)
        PartSummaryView nextPart,
        @Schema(description = "Kết quả ghi nhận finalAnswers", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FinalAnswerResult> results) {

    public FinishPartResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
