package vn.unicom.thi360.questionbank.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import vn.unicom.thi360.shared.security.RequirePermission;
import vn.unicom.thi360.questionbank.application.AvailableQuestionCountService;
import vn.unicom.thi360.questionbank.spi.AnswerFormat;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Tag(name = "Exam", description = "Dữ liệu tham chiếu và bài thi")
@RestController
@RequestMapping("/api/v1/admin/exams/available-questions")
public class AvailableQuestionCountController {
    private final AvailableQuestionCountService service;

    public AvailableQuestionCountController(AvailableQuestionCountService service) {
        this.service = service;
    }

    @Schema(name = "AvailableQuestionsKnowledgeScope", description = "Phạm vi kiến thức có hiệu lực của nhóm")
    public record KnowledgeScope(List<@NotNull UUID> nodeIds, List<String> competencyCodes,
                                 Boolean everyChildInScope,
                                 @Schema(description = "MAPID nodes (and their descendants) the group must not draw from: "
                                         + "ONLY_IN_GROUP of another group (UNI-2758)")
                                 List<@NotNull UUID> excludedNodeIds,
                                 @Schema(description = "SOURCE rule of a MAPID matrix: a set counts only when all its "
                                         + "children carry the same node of the scope (a descendant counts); "
                                         + "stricter than everyChildInScope (UNI-2758)")
                                 Boolean everyChildSameNode) {
        public KnowledgeScope(List<UUID> nodeIds, List<String> competencyCodes, Boolean everyChildInScope) {
            this(nodeIds, competencyCodes, everyChildInScope, null, null);
        }
    }

    @Schema(name = "AvailableQuestionsItemShape")
    public record ItemShape(Integer statements, Integer leftItems, Integer rightItems, Integer options) {}

    @Schema(name = "AvailableQuestionsChildCondition")
    public record ChildCondition(@Min(1) @Max(QuestionSourcePort.MAX_SET_CHILDREN) int from,
                                 @Min(1) @Max(QuestionSourcePort.MAX_SET_CHILDREN) int to,
                                 List<@NotNull UUID> nodeIds, QuestionType answerType,
                                 @Valid ItemShape itemShape) {
        @AssertTrue
        @JsonIgnore
        @Schema(hidden = true)
        public boolean isOrdered() {
            return from <= to;
        }
    }

    @Schema(name = "AvailableQuestionsQuery", description = "Bộ lọc nguồn câu hỏi của một nhóm")
    public record Query(@NotBlank String subjectCode, QuestionType questionType,
                        List<QuestionType> answerTypes, QuestionType childAnswerType,
                        @Valid KnowledgeScope knowledgeScope, List<String> tagCodes,
                        List<String> competencyCodes, BigDecimal requiredPointsPerQuestion,
                        ItemShape itemShape, List<@NotNull AnswerFormat> answerFormats,
                        List<@NotNull @Valid ChildCondition> childConditions,
                        QuestionSourcePort.AudioRequirement audioRequirement) {}

    @Schema(name = "AvailableQuestionsSubjectQuery", description = "Các nhóm nguồn của một môn")
    public record SubjectQuery(@NotBlank String subjectCode, @NotNull List<@Valid Query> groups) {}

    @Schema(name = "AvailableQuestionsCell")
    public record Cell(String gradeCode, String difficultyCode, long value) {}
    @Schema(name = "AvailableQuestionsAnswerType")
    public record AnswerType(QuestionType answerType, long value) {}
    @Schema(name = "AvailableQuestionsCompetency")
    public record Competency(String code, long value) {}
    @Schema(name = "AvailableQuestionsResponse")
    public record Response(long total, long commandCount, List<Cell> cells, List<AnswerType> byAnswerType,
                           List<Competency> byCompetency) {
        public Response(long total, List<Cell> cells, List<AnswerType> byAnswerType,
                        List<Competency> byCompetency) {
            this(total, total, cells, byAnswerType, byCompetency);
        }
    }

    @PostMapping("/count")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "countAvailableQuestions", summary = "Đếm nguồn câu hỏi của nhóm")
    public Response count(@Valid @RequestBody Query query) {
        return service.count(query);
    }

    @PostMapping("/count-subject")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "countSubjectAvailableQuestions", summary = "Đếm hợp nguồn câu hỏi của môn")
    public Response countSubject(@Valid @RequestBody SubjectQuery query) {
        return service.countSubject(query);
    }
}
