package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "ExamVersionResponse", description = "Một phiên bản của bài thi")
public record ExamVersionResponse(
        @Schema(description = "Số phiên bản; null = bản làm việc")
        Integer version,
        @Schema(description = "WORKING | SERVING | RETIRED", requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
        @Schema(description = "Thời điểm phát hành")
        Instant publishedAt,
        @Schema(description = "Người phát hành")
        PersonRef publishedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptCount,
        @Schema(description = "Ghi chú phát hành")
        String note,
        List<ExamVersionPath> publishedPaths,
        List<ExamVersionPath> servingPaths
) {
}
