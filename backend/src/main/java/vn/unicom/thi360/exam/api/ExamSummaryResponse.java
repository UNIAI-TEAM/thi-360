package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bài thi trong danh sách (spec 9.2 listExam)")
public record ExamSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mã kỳ thi (EXAM item code)", requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,
        @Schema(description = "Tên kỳ thi")
        String kindName,
        @Schema(description = "FULL | PART | SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        String scope,
        @Schema(description = "DRAFT | PUBLISHED | DISCONTINUED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(description = "Phiên bản đang phục vụ; null khi chưa phát hành")
        Integer servingVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasUnpublishedChanges,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int questionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasEssay,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasEnglishOption,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int combinationCount,
        @Schema(description = "Thời lượng chung (SHARED); PER_PART thì tính từ các phần ở client")
        Integer durationMinutes,
        @Schema(description = "Thang điểm")
        BigDecimal maxScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ExamCheckSummary check,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Người cập nhật gần nhất")
        PersonRef updatedBy
) {
}
