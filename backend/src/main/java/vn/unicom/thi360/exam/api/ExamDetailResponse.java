package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;

@Schema(description = "Chi tiết bài thi kèm cấu trúc làm việc (spec 9.2 ExamDetail)")
public record ExamDetailResponse(
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
        @Schema(description = "Mã phần khi scope = PART")
        String partCode,
        @Schema(description = "Mã môn khi bài theo môn")
        String subjectCode,
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
        @Schema(description = "PER_PART | SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
        String timing,
        @Schema(description = "Thời lượng chung khi timing = SHARED")
        Integer durationMinutes,
        @Schema(description = "Thang điểm")
        BigDecimal maxScore,
        @Schema(description = "Thang điểm quy chiếu (V-ACT)")
        BigDecimal referenceMax,
        @Schema(description = "Mặc định chấm luyện tập", requiredMode = Schema.RequiredMode.REQUIRED)
        String practiceScoring,
        @Schema(description = "Mã nguyên mẫu đã nạp; null khi cấu trúc tự dựng")
        String templateCode,
        @Schema(description = "STANDARD | REVIEW", allowableValues = {"STANDARD", "REVIEW"})
        String buildMode,
        @Schema(description = "Năm cấu trúc")
        Integer structureYear,
        @Schema(description = "Năm ma trận")
        Integer matrixYear,
        @Schema(description = "Người dùng đã xác nhận năm cấu trúc ≠ năm ma trận",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean yearsConfirmed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ExamCheckSummary check,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Người cập nhật gần nhất")
        PersonRef updatedBy,
        @Schema(description = "Mô tả hiển thị trên danh sách bán")
        String description,
        @Schema(description = "Hướng dẫn làm bài (HTML đã lọc theo chính sách)")
        String instructions,
        @Schema(description = "Cấu trúc đang chỉnh của bản làm việc",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamPart> parts,
        @Schema(description = "Các phiên bản (bản làm việc + bản đã chốt; BE-6 bổ sung bản chốt)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamVersionResponse> versions,
        @Schema(description = "Revision hiện tại của bài thi — gửi lại trong expectedRevision",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long revision
) {
    public ExamDetailResponse {
        parts = parts == null ? List.of() : List.copyOf(parts);
        versions = versions == null ? List.of() : List.copyOf(versions);
    }
}
