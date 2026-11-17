package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(description = "Lệnh sửa câu hỏi; expectedRevision là khóa lạc quan (D13)")
public record UpdateQuestionRequest(
        @NotNull Long expectedRevision,
        QuestionType type,
        String subjectCode,
        String gradeCode,
        String difficultyCode,
        String taskTypeCode,
        String competencyCode,
        BigDecimal points,
        @Valid List<KnowledgeRef> knowledge,
        List<String> tagCodes,
        String content,
        String solution,
        List<String> commonMistakes,
        List<UUID> attachmentIds,
        QuestionBody body,
        String sourceCode) {}
