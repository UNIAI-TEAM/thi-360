package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

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
                implementation = ExamStructureDocument.class, requiredMode = Schema.RequiredMode.REQUIRED)
        JsonNode structure,
        @Schema(description = "Mẫu cho phép công bố bài (false: chỉ lưu nháp)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean publishable,
        @Schema(description = "Mẫu khóa số câu từng phần/mục (kiểm tra báo STRUCTURE_COUNT_LOCKED)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean fixedCounts,
        @Schema(description = "Mẫu khóa dạng, số câu, số chùm, cỡ chùm từng nhóm của bài phạm vi SUBJECT "
                + "(kiểm tra báo BLOCK_COUNT_LOCKED); bài PART và bài ôn từ cùng mẫu không bị khóa",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean fixedBlockCounts,
        @Schema(description = "Nhóm đã lược của mẫu (phần viết) không bật lại được "
                + "(kiểm tra báo OMITTED_BLOCK_LOCKED); tạo bài PART chỉ gồm nhóm đã lược bị từ chối",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean omittedLocked,
        @Schema(description = "Mỗi phần của mẫu kèm số câu theo dạng câu (cho dòng thay đổi theo dạng câu khi đổi phiên bản)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<PartSummary> parts
) {
    public ExamTemplateResponse {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    @Schema(name = "ExamTemplatePartSummary")
    public record PartSummary(
            @Schema(description = "Id phần trong mẫu; null khi mẫu chưa gán id")
            String partId,
            Integer order,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Số câu của phần theo mẫu; null khi mẫu không ghi")
            Integer questionCount,
            @Schema(description = "Số câu theo dạng câu của các nhóm không lược trong phần, theo thứ tự xuất hiện; "
                    + "phần tự chọn tính theo lựa chọn đầu tiên; nhóm không ghi dạng câu có dạng ANY",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            List<QuestionTypeCount> questionTypes) {
        public PartSummary {
            questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
        }
    }

    @Schema(name = "ExamTemplateQuestionTypeCount")
    public record QuestionTypeCount(
            @Schema(description = "Mã dạng câu (SINGLE_CHOICE, TRUE_FALSE, MATCHING, QUESTION_SET ...) hoặc ANY",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String questionType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int questionCount) {
    }
}
