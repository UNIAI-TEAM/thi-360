package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "AttemptResultResponse",
        description = "Kết quả lượt thi của học viên (LT §10.1, F14–F16, F20; không chứa đáp án hay hướng dẫn chấm)")
public record AttemptResultResponse(
        UUID attemptId,
        UUID examId,
        int examVersionNo,
        String examName,
        String examKindCode,
        String scope,
        String partCode,
        String subjectCode,
        Map<String, AttemptChoiceSummary> choices,
        Integer attemptNo,
        AttemptState state,
        boolean regrading,
        String regradingLabel,
        String pendingReviewLabel,
        String statusMessage,
        EndReason endReason,
        Instant startedAt,
        Instant finishedAt,
        Instant gradedAt,
        String practiceScoring,
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal rawMaxScore,
        BigDecimal scaleMax,
        String scoreLabel,
        @Schema(description = "Ghi chú cố định dưới điểm luyện tập V-ACT (BS §3)", nullable = true)
                String practiceScoreNote,
        String resultView,
        boolean reviewAvailable,
        String faultFlag,
        String faultReason,
        boolean compensated,
        String faultMessage,
        UnitStatsView statistics,
        List<PartResultView> parts,
        List<SubjectResultView> subjects,
        List<VactComponentView> vactComponents,
        List<SegmentResultView> segments) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptResultUnitStats")
    public record UnitStatsView(
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int pendingReviewCount,
            int totalCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptResultPart")
    public record PartResultView(
            UUID partId,
            String partCode,
            String partName,
            int ord,
            boolean skipped,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxScore,
            UnitStatsView stats) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptResultSubject")
    public record SubjectResultView(
            String code,
            String name,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxScore,
            UnitStatsView stats) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptResultVactSubsection")
    public record VactSubsectionView(
            String sectionId,
            String label,
            int questionCount,
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int totalCount,
            @Schema(description = "Tỷ lệ đúng (0–100%)")
                    BigDecimal correctRate) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptResultVactComponent")
    public record VactComponentView(
            String componentCode,
            String name,
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int totalCount,
            @Schema(description = "Tỷ lệ đúng (0–100%)")
                    BigDecimal correctRate,
            List<VactSubsectionView> subsections) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "AttemptResultSegment",
            description = "Kết quả theo phân đoạn hiển thị của bài thi (BS §17.3, AT-7)")
    public record SegmentResultView(
            String code,
            String name,
            String partCode,
            int fromPosition,
            int toPosition,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxPoints,
            UnitStatsView stats) {}
}
