package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.AvailableQuestionCountService;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam", description = "Dữ liệu tham chiếu và bài thi")
@RestController
@RequestMapping("/api/v1/admin/exams/available-questions")
public class AvailableQuestionCountController {
    private final AvailableQuestionCountService service;

    public AvailableQuestionCountController(AvailableQuestionCountService service) {
        this.service = service;
    }

    @Schema(description = "Phạm vi kiến thức có hiệu lực của nhóm")
    public record KnowledgeScope(List<UUID> nodeIds, String match, List<String> competencyCodes) {}

    @Schema(description = "Bộ lọc nguồn câu hỏi của một nhóm")
    public record Query(@NotBlank String subjectCode, QuestionType questionType,
                        List<QuestionType> answerTypes, QuestionType childAnswerType,
                        KnowledgeScope knowledgeScope, String taskTypeCode, List<String> tagCodes,
                        List<String> competencyCodes, BigDecimal requiredPointsPerQuestion) {}

    @Schema(description = "Các nhóm nguồn của một môn")
    public record SubjectQuery(@NotBlank String subjectCode, @NotNull List<@Valid Query> groups) {}

    public record Cell(String gradeCode, String difficultyCode, long value) {}
    public record AnswerType(QuestionType answerType, long value) {}
    public record Competency(String code, long value) {}
    public record Response(long total, List<Cell> cells, List<AnswerType> byAnswerType,
                           List<Competency> byCompetency) {}

    @PostMapping("/count")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Đếm nguồn câu hỏi của nhóm")
    public Response count(@Valid @RequestBody Query query) {
        return service.count(query);
    }

    @PostMapping("/count-subject")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Đếm hợp nguồn câu hỏi của môn")
    public Response countSubject(@Valid @RequestBody SubjectQuery query) {
        return service.countSubject(query);
    }
}
