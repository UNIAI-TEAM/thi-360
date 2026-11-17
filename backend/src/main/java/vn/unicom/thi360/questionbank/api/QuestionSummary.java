package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(description = "Câu hỏi dạng tóm tắt, phần chung của danh sách và chi tiết")
public record QuestionSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionStatus status,
        @Schema(description = "Id bộ chứa câu (chỉ câu con)") UUID parentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentPreview,
        String subjectCode,
        String gradeCode,
        String difficultyCode,
        String taskTypeCode,
        String competencyCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal points,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KnowledgeRef> knowledge,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tagCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasSolution,
        @Schema(description = "Số phiên bản nội dung đang phục vụ; 0 khi chưa có",
                requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(description = "Số phiên bản Nháp đang sửa; null khi không có") Integer workingVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long usedInExamCount,
        String sourceCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long revision,
        PersonRef createdBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {}
