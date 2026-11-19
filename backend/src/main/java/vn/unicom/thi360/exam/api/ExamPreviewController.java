package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ExamPreviewService;
import vn.unicom.thi360.exam.domain.DisplaySegment;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Xem trước một đường của bài thi bằng bộ giải sinh đề có hạt giống (spec 5.2/5.3).
 * Đề sinh ra ghi sổ {@code question_usage_ledger} với kind PREVIEW (D18).
 */
@Tag(name = "Exam", description = "Bài thi, cấu trúc và mẫu")
@RestController
@RequestMapping("/api/v1/admin")
public class ExamPreviewController {

    private final ExamPreviewService preview;

    public ExamPreviewController(ExamPreviewService preview) {
        this.preview = preview;
    }

    @Schema(name = "ExamPreviewTarget")
    public record PreviewTarget(
            @Schema(allowableValues = {"SECTION", "PATH", "EXAM"}, requiredMode = Schema.RequiredMode.REQUIRED)
            String kind, String sectionId, String pathId, List<String> subjectCodes, Integer sectionQuestionCount) {
    }

    @Schema(name = "ExamPreviewRequest")
    public record GeneratePreviewRequest(
            @Schema(deprecated = true, description = "Legacy path selection; use target when BE-8 is available")
            String pathId,
            Long seed,
            Integer version,
            @Schema(deprecated = true, description = "Legacy subject selection; use target.subjectCodes")
            List<String> subjectCodes,
            @Schema(nullable = true) PreviewTarget target,
            UUID basePreviewId,
            List<String> regeneratePartIds) {
    }

    /** Một lựa chọn của phần tự chọn trên đường (PathChoice của web). */
    @Schema(name = "ExamPreviewPathChoice")
    public record PathChoice(String partId, String optionId, String kind, String code,
                             String name) {
    }

    @Schema(name = "ExamPreviewPathRef")
    public record PathRef(String pathId, List<PathChoice> choices) {
    }

    @Schema(name = "ExamPreviewOption")
    public record PreviewOption(String id, String content, boolean correct) {
    }

    @Schema(name = "ExamPreviewKnowledgeRef")
    public record KnowledgeRef(String nodeId, String code, String name) {
    }

    @Schema(name = "ExamPreviewRubricItem")
    public record RubricItem(String id, String description, BigDecimal points) {
    }

    /** Tệp đính kèm của câu gốc; phát qua {@code GET /api/v1/admin/question-assets/{id}}. */
    @Schema(name = "ExamPreviewAttachment")
    public record PreviewAttachment(String id, String name, String mime) {
    }

    /** Một câu (câu đơn hoặc câu con của bộ) trong đề xem trước. */
    @Schema(name = "ExamPreviewQuestion")
    public record PreviewQuestion(int number, String questionId, String code, Integer version,
                                  String type, boolean simulated, String content,
                                  String gradeCode, String difficultyCode, KnowledgeRef knowledge,
                                  BigDecimal points, List<PreviewOption> options, String answer,
                                  String solution, List<RubricItem> rubric, JsonNode body,
                                  @Schema(types = {"array", "null"}, description = "Displayed option IDs in order; PaperOrdering never shuffles when shuffleOptions=false")
                                  List<String> optionOrder,
                                  @Schema(types = {"array", "null"}, description = "Option IDs in the author's original order")
                                  List<String> originalOptionOrder,
                                  @Schema(types = {"integer", "null"}, description = "Original solver-selected question position")
                                  Integer originalNumber,
                                  @Schema(types = {"array", "null"}, description = "Topic quota IDs assigned to this question")
                                  List<String> assignedTopicQuotaIds,
                                  @Schema(description = "Attachments of a single question; empty for a child of a set")
                                  List<PreviewAttachment> attachments) {
    }

    /** Một mục trên đề: câu đơn ({@code kind=QUESTION}) hoặc nguyên bộ ({@code kind=SET}). */
    @Schema(name = "ExamPreviewItem")
    public record PreviewItem(String kind, PreviewQuestion question, String setId, String code,
                              Integer version, boolean simulated, String passage,
                              List<PreviewQuestion> children,
                              @Schema(types = {"integer", "null"}, description = "Original solver-selected item position")
                              Integer originalOrder,
                              @Schema(description = "Attachments of the set's root question; empty for a single question")
                              List<PreviewAttachment> attachments) {
    }

    @Schema(name = "ExamPreviewAllocationCell")
    public record AllocationCell(String gradeCode, String difficultyCode, Integer required,
                                 int generated) {
    }

    /** Chỉ tiêu nguyên của khối (COMBINED dùng cells, SEPARATE dùng byGrade/byDifficulty). */
    @Schema(name = "ExamPreviewRequirementCell")
    public record RequirementCell(String gradeCode, String difficultyCode, int required) {
    }

    @Schema(name = "ExamPreviewRequirementDimension")
    public record RequirementDimension(String code, int required) {
    }

    @Schema(name = "ExamPreviewBlockRequirement")
    public record BlockRequirement(String mode, List<RequirementCell> cells,
                                   List<RequirementDimension> byGrade,
                                   List<RequirementDimension> byDifficulty,
                                   List<RequirementDimension> byCompetency) {
    }

    @Schema(name = "ExamPreviewBlock")
    public record PreviewBlock(String blockId, String blockName, String subjectCode,
                               String questionType, boolean manualGrading, BigDecimal pointsEach,
                               int firstNumber, int lastNumber, int questionCount,
                               Integer setCount, Integer setSize, BlockRequirement requirement,
                               List<AllocationCell> allocation, List<PreviewItem> items) {
    }

    @Schema(name = "ExamPreviewPart")
    public record PreviewPart(String partId, String name, Integer order,
                              Integer durationMinutes, int firstNumber, int lastNumber,
                              BigDecimal points, BigDecimal maxPoints,
                              List<PreviewBlock> blocks,
                              @Schema(description = "Declared display segments of the part; empty when it has none")
                              List<DisplaySegment> displaySegments) {
    }

    @Schema(name = "ExamPreviewCheck")
    public record PreviewCheck(String code, String status, BigDecimal value) {
    }

    @Schema(name = "ExamPreviewKnowledgeStat")
    public record KnowledgeStat(String nodeId, String code, String name, String subjectCode,
                                int count) {
    }

    /** Chỉ tiêu cấp môn: yêu cầu đã quy ra số câu và đạt được trên đề sinh. */
    @Schema(name = "ExamPreviewSubjectQuotaRow")
    public record SubjectQuotaRow(String answerType, int required, int achieved) {
    }

    @Schema(name = "ExamPreviewSubjectDimensionRow")
    public record SubjectDimensionRow(String code, int required, int achieved) {
    }

    @Schema(name = "ExamPreviewSubjectAllocation")
    public record SubjectAllocation(String sectionId, String subjectCode, String optionId,
                                    Integer questionCount, List<SubjectQuotaRow> answerQuotas,
                                    List<SubjectDimensionRow> byGrade,
                                    List<SubjectDimensionRow> byDifficulty,
                                    List<AllocationCell> cells) {
    }

    @Schema(name = "ExamPreviewPaper")
    public record PreviewPaper(int totalQuestions, BigDecimal totalPoints, int totalMinutes,
                               boolean sharedDuration, boolean hasManualGrading,
                               List<PreviewPart> parts, List<PreviewCheck> checks,
                               List<KnowledgeStat> knowledgeStats,
                               List<SubjectAllocation> subjects) {
    }

    /** Vấn đề của đường hỏng (hình dạng CheckIssue của web; một phần trường không áp dụng). */
    @Schema(name = "ExamPreviewIssue")
    public record PreviewIssue(String key, String code, String severity, String group,
                               String partId, String partName, String sectionId, String subjectCode,
                               String blockId, String blockName, String optionId, String gradeCode,
                               String difficultyCode, String dimension, String competencyCode,
                               String answerType, Integer subjectCount, BigDecimal required,
                               BigDecimal available, Integer timeLimitSeconds,
                               List<String> questionCodes, ExamCheckApiModels.Location location,
                               @Schema(types = {"string", "null"}, description = "Display segment code of DISPLAY_SEGMENT_MISMATCH")
                               String segmentCode) {
    }

    @Schema(name = "ExamPreviewCompliance")
    public record Compliance(String criterion, String scope, BigDecimal required, BigDecimal actual,
                             String enforcement, String basis, String note) {
    }

    /** {@code status=GENERATED} kèm {@code paper}; {@code status=FAILED} kèm {@code reason,issues}. */
    @Schema(name = "ExamPreviewResult")
    public record PreviewResult(UUID examId, PathRef path, Integer version, long seed,
                                Instant generatedAt, String status, PreviewPaper paper,
                                String reason, List<PreviewIssue> issues,
                                @Schema(types = {"string", "null"}, format = "uuid") UUID previewId,
                                @Schema(types = {"array", "null"}) List<Compliance> compliance,
                                @Schema(types = {"array", "null"}) List<PreviewQuestion> droppedQuestions) {
    }

    @PostMapping("/exams/{id}/preview")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "previewExam", summary = "Sinh đề xem trước một đường (có hạt giống)")
    public PreviewResult preview(@PathVariable UUID id,
            @Valid @RequestBody GeneratePreviewRequest request) {
        if (request.target() != null) {
            if ((request.basePreviewId() == null) != (request.regeneratePartIds() == null)) {
                throw new ValidationException((String) null);
            }
            PreviewTarget target = request.target();
            if (!"SECTION".equals(target.kind()) && !"PATH".equals(target.kind()) && !"EXAM".equals(target.kind())) {
                throw new ValidationException((String) null);
            }
            if ("SECTION".equals(target.kind()) && (target.sectionId() == null || target.sectionId().isBlank())) {
                throw new ValidationException((String) null);
            }
            String pathId = "PATH".equals(target.kind()) ? target.pathId() : "default";
            if (pathId == null || pathId.isBlank()) throw new ValidationException((String) null);
            long seed = request.seed() == null ? java.util.concurrent.ThreadLocalRandom.current().nextLong() : request.seed();
            request = new GeneratePreviewRequest(pathId, seed, request.version(),
                    "SECTION".equals(target.kind()) ? null : target.subjectCodes(), target,
                    request.basePreviewId(), request.regeneratePartIds());
        } else if (request.pathId() == null || request.pathId().isBlank() || request.seed() == null
                || request.basePreviewId() != null || request.regeneratePartIds() != null) {
            throw new ValidationException((String) null);
        }
        return preview.generate(id, request);
    }
}
