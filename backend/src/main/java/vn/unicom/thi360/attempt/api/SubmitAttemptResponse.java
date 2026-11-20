package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;

@Schema(description = "Kết quả nộp bài hoặc trạng thái đóng đã ghi nhận của lượt thi (LT §6.3)")
public record SubmitAttemptResponse(
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow,
        @Schema(description = "Id lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID attemptId,
        @Schema(description = "Trạng thái của lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptState state,
        @Schema(description = "Lý do kết thúc lượt thi", nullable = true)
        EndReason endReason,
        @Schema(description = "Thời điểm kết thúc lượt thi", nullable = true)
        Instant finishedAt,
        @Schema(description = "Trạng thái các phần sau khi đóng lượt", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PartSummaryView> parts,
        @Schema(description = "Kết quả ghi nhận finalAnswers", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FinalAnswerResult> results) {

    public SubmitAttemptResponse {
        parts = parts == null ? List.of() : List.copyOf(parts);
        results = results == null ? List.of() : List.copyOf(results);
    }
}
