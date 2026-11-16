package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Một kỳ thi trong danh mục EXAM kèm hồ sơ (spec 6)")
public record ExamKindOption(
        @Schema(description = "Id giá trị danh mục EXAM", requiredMode = Schema.RequiredMode.REQUIRED)
        java.util.UUID itemId,
        @Schema(description = "Mã kỳ thi (HSA, QDA…)", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Phạm vi bài thi được phép: FULL, PART, SUBJECT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> scopes,
        @Schema(description = "KIND: một mẫu cho cả kỳ thi; SUBJECT: mẫu theo môn",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String templateBy,
        @Schema(description = "PER_PART | SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
        String timing,
        @Schema(description = "Thang điểm mặc định của kỳ thi")
        BigDecimal maxScore,
        @Schema(description = "Thang điểm quy chiếu (V-ACT)")
        BigDecimal referenceMax,
        @Schema(description = "Mặc định chấm luyện tập: IMMEDIATE | AFTER_EXAM",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String practiceScoring,
        @Schema(description = "Bảng điểm Đúng/Sai nhiều ý (số điểm theo số ý đúng)")
        JsonNode trueFalseScoring,
        @Schema(description = "Quy tắc đa lựa chọn: ALL_OR_NOTHING | PARTIAL | PENALTY")
        String multipleChoiceRule,
        @Schema(description = "Đã có hồ sơ (PUT) hay chưa", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean profileSet
) {
    public ExamKindOption {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
