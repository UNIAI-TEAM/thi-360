package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Tạo bài thi Nháp theo phạm vi (spec 5.2, 9.2)")
public record CreateExamRequest(
        @Schema(description = "Mã kỳ thi (giá trị nhóm EXAM)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String kind,
        @Schema(description = "FULL | PART | SUBJECT; phải nằm trong scopes của hồ sơ kỳ thi",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String scope,
        @Schema(description = "Mã phần; bắt buộc khi scope = PART")
        @Size(max = 30)
        String partCode,
        @Schema(description = "Mã môn; bắt buộc khi kỳ thi mẫu theo môn (templateBy = SUBJECT)")
        @Size(max = 30)
        String subjectCode,
        @Schema(description = "Mã bài thi (duy nhất, không phân biệt hoa thường)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String code,
        @Schema(description = "Tên bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String name,
        @Schema(description = "Thời lượng chung; bắt buộc khi timing = SHARED và mẫu không khai báo")
        @Positive
        Integer durationMinutes,
        @Schema(description = "Thang điểm; mặc định theo mẫu rồi hồ sơ kỳ thi")
        @Positive
        BigDecimal maxScore,
        @Schema(description = "Mô tả")
        @Size(max = 4000)
        String description,
        @Schema(description = "Hướng dẫn làm bài (HTML; qua ExamInstructionsPolicy)")
        @Size(max = 100000)
        String instructions,
        @Schema(description = "STANDARD | REVIEW", allowableValues = {"STANDARD", "REVIEW"})
        String buildMode,
        @Schema(description = "Cấu trúc tổng quan")
        List<ExamOverviewRequest.Row> overview
) {
}
