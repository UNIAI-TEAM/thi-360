package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Thay thế toàn bộ hồ sơ của một kỳ thi (PUT /exam-kinds/{itemId}, spec 6)")
public record UpdateExamKindProfileRequest(
        @Schema(description = "Phạm vi được phép; ít nhất một trong FULL, PART, SUBJECT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<@NotBlank String> scopes,
        @Schema(description = "KIND | SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String templateBy,
        @Schema(description = "PER_PART | SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String timing,
        @Schema(description = "Thang điểm của bài; bắt buộc dương nếu khai báo")
        @Positive
        BigDecimal maxScore,
        @Schema(description = "Thang điểm quy chiếu (V-ACT)")
        @Positive
        BigDecimal referenceMax,
        @Schema(description = "IMMEDIATE | AFTER_EXAM", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String practiceScoring,
        @Schema(description = "Bảng điểm Đúng/Sai nhiều ý; null khi kỳ thi không dùng")
        JsonNode trueFalseScoring,
        @Schema(description = "ALL_OR_NOTHING | PARTIAL | PENALTY; null khi không dùng")
        @Size(max = 30)
        String multipleChoiceRule,
        @Schema(description = "Revision đang xem; lệch → 412 EXAM_REVISION_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long expectedRevision
) {
}
