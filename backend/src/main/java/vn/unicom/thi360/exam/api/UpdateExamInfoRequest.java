package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Sửa thông tin chung của bài thi (PATCH /exams/{id}, spec 9.2); trường null = giữ nguyên")
public record UpdateExamInfoRequest(
        @Schema(description = "Mã bài thi (duy nhất)")
        @Size(max = 30)
        String code,
        @Schema(description = "Tên bài thi")
        @Size(max = 200)
        String name,
        @Schema(description = "Mã kỳ thi; đổi khi đã phát hành → 409 EXAM_SCOPE_LOCKED")
        @Size(max = 30)
        String kind,
        @Schema(description = "FULL | PART | SUBJECT; đổi khi đã phát hành → 409 EXAM_SCOPE_LOCKED")
        @Size(max = 30)
        String scope,
        @Schema(description = "Mã phần khi scope = PART")
        @Size(max = 30)
        String partCode,
        @Schema(description = "Mã môn")
        @Size(max = 30)
        String subjectCode,
        @Schema(description = "Năm cấu trúc")
        Integer structureYear,
        @Schema(description = "Năm ma trận")
        Integer matrixYear,
        @Schema(description = "Người dùng xác nhận năm cấu trúc ≠ năm ma trận")
        Boolean yearsConfirmed,
        @Schema(description = "Bắt buộc true khi đổi phạm vi hoặc cách dựng bài sau khi cấu trúc đã sửa; "
                + "thiếu → 409 EXAM_STRUCTURE_REPLACE_CONFIRM")
        Boolean confirmReplace,
        @Schema(description = "STANDARD | REVIEW; đổi → dựng lại cấu trúc như tạo mới "
                + "(cần confirmReplace như đổi phạm vi); đã phát hành → 409 EXAM_SCOPE_LOCKED; "
                + "REVIEW chỉ khi scope = SUBJECT",
                allowableValues = {"STANDARD", "REVIEW"})
        String buildMode,
        @Schema(description = "Thời lượng chung (SHARED)")
        @Positive
        Integer durationMinutes,
        @Schema(description = "Thang điểm; phải dương")
        @Positive
        BigDecimal maxScore,
        @Schema(description = "RAW | LINEAR", allowableValues = {"RAW", "LINEAR"})
        String practiceScoring,
        @Schema(description = "Thang điểm quy chiếu khi quy đổi")
        @Positive
        BigDecimal referenceMax,
        @Schema(description = "Xác nhận quy đổi điểm sang LINEAR")
        Boolean confirmScoring,
        @Schema(description = "Mô tả; chuỗi trống → xóa")
        @Size(max = 4000)
        String description,
        @Schema(description = "Hướng dẫn làm bài (HTML; qua ExamInstructionsPolicy); chuỗi trống → xóa")
        @Size(max = 100000)
        String instructions,
        @Schema(description = "Revision đang xem; lệch → 412 EXAM_REVISION_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long expectedRevision
) {
    /** Callers that only patch info/scoring and never the build mode (devdata builders) use this arity. */
    public UpdateExamInfoRequest(String code, String name, String kind, String scope, String partCode,
            String subjectCode, Integer structureYear, Integer matrixYear, Boolean yearsConfirmed,
            Boolean confirmReplace, Integer durationMinutes, BigDecimal maxScore, String practiceScoring,
            BigDecimal referenceMax, Boolean confirmScoring, String description, String instructions,
            Long expectedRevision) {
        this(code, name, kind, scope, partCode, subjectCode, structureYear, matrixYear, yearsConfirmed,
                confirmReplace, null, durationMinutes, maxScore, practiceScoring, referenceMax,
                confirmScoring, description, instructions, expectedRevision);
    }
}
