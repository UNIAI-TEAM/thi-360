package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(description = "Thân yêu cầu tạo hoặc sửa câu hỏi (spec 4.5, 9.2)")
public record SaveQuestionRequest(
        QuestionType type,
        String subjectCode,
        String gradeCode,
        String difficultyCode,
        String taskTypeCode,
        String competencyCode,
        @Schema(description = "Điểm; mặc định 1. Bộ câu hỏi và Điền khuyết: tổng thành phần") BigDecimal points,
        @Valid List<KnowledgeRef> knowledge,
        List<String> tagCodes,
        String content,
        String solution,
        List<String> commonMistakes,
        List<UUID> attachmentIds,
        QuestionBody body,
        @Schema(description = "Mã nguồn khi nhập từ tệp (không sửa qua API soạn tay)") String sourceCode) {}
