package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Nguyên mẫu cấu trúc bài thi (exam_template; spec 4, 5.2)")
public record ExamTemplateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Mã kỳ thi áp dụng", requiredMode = Schema.RequiredMode.REQUIRED)
        String kindCode,
        @Schema(description = "FULL | PART | SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        String scope,
        @Schema(description = "Mã phần khi scope = PART")
        String partCode,
        @Schema(description = "Mã môn khi mẫu theo môn")
        String subjectCode,
        @Schema(description = "Nhãn phiên bản cấu trúc")
        String label,
        @Schema(description = "Tóm tắt phiên bản cấu trúc")
        String summary,
        @Schema(description = "STANDARD | REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        String buildMode,
        @Schema(description = "Phiên bản mặc định", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isDefault,
        @Schema(description = "Năm cấu trúc")
        Integer structureYear,
        @Schema(description = "Năm ma trận")
        Integer matrixYear,
        @Schema(description = "PER_PART | SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
        String timing,
        @Schema(description = "Thời lượng chung khi timing = SHARED")
        Integer durationMinutes,
        @Schema(description = "Thang điểm")
        BigDecimal maxScore,
        @Schema(description = "Ghi chú của chuyên gia khi dựng mẫu")
        String expertNote,
        @Schema(description = "Có đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        @Schema(description = "Tài liệu cấu trúc nguyên bản {parts: [...]} (id để null — client sinh)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        JsonNode structure,
        @Schema(description = "Mẫu cho phép công bố bài (false: chỉ lưu nháp)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean publishable,
        @Schema(description = "Mẫu khóa số câu từng phần/mục (kiểm tra báo STRUCTURE_COUNT_LOCKED)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean fixedCounts
) {
}
