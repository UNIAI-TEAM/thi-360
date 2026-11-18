package vn.unicom.thi360.exam.api;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** OpenAPI shapes for the structured checker result. */
public final class ExamCheckApiModels {
    private ExamCheckApiModels() { }

    @Schema(name = "ExamIssueLocation")
    public record Location(String partId, String sectionId, String blockId, Integer slotIndex,
                           Integer childPosition, String pathId) { }
    @Schema(name = "ExamSectionState")
    public record SectionState(String partId, String sectionId, String subjectCode,
            @Schema(allowableValues = {"INCOMPLETE", "CHECKED", "NEEDS_CHECK"}) String state) { }

    @Schema(name = "ExamCheckIssue")
    public record Issue(String key, String code, String severity, String group, String partId,
                        String partName, String sectionId, String subjectCode, String blockId,
                        String blockName, String optionId, String gradeCode, String difficultyCode,
                        String dimension, String competencyCode, String answerType, Integer subjectCount,
                        BigDecimal required, BigDecimal available, Long timeLimitSeconds, List<String> questionCodes,
                        BigDecimal excludedPoints, Location location, List<String> affectedCodes) { }
    @Schema(name = "ExamCheckGroup")
    public record Group(String group, String status, List<Issue> issues) { }
    @Schema(name = "ExamCheckChoice")
    public record Choice(String partId, String optionId, String kind, String code, String name) { }
    @Schema(name = "ExamCheckDimension")
    public record Dimension(String code, @Schema(format = "decimal") BigDecimal required) { }
    @Schema(name = "ExamCheckCell")
    public record Cell(String gradeCode, String difficultyCode, @Schema(format = "decimal") BigDecimal required) { }
    @Schema(name = "ExamCheckRequirement")
    public record Requirement(String mode, List<Cell> cells, List<Dimension> byGrade,
                              List<Dimension> byDifficulty, List<Dimension> byCompetency) { }
    @Schema(name = "ExamCheckBlock")
    public record Block(String partId, String partName, Integer partOrder, String sectionId,
                        String subjectCode, String optionId, String blockId, String blockName,
                        String questionType, int required, Integer setCount, Integer setSize,
                        Long available, Long availableSets, Requirement requirement, String result, List<Issue> issues) { }
    @Schema(name = "ExamCheckAnswerQuota")
    public record AnswerQuota(String answerType, int required, Integer achieved) { }
    @Schema(name = "ExamCheckSubjectDimension")
    public record SubjectDimension(String code, @Schema(format = "decimal") BigDecimal required,
                                   @Schema(format = "decimal") BigDecimal achieved) { }
    @Schema(name = "ExamCheckSubject")
    public record Subject(String sectionId, String subjectCode, String optionId, Integer questionCount,
                          List<AnswerQuota> answerQuotas, List<SubjectDimension> byGrade,
                          List<SubjectDimension> byDifficulty) { }
    @Schema(name = "ExamCheckSelection")
    public record Selection(List<String> subjectCodes, List<Integer> counts, String result, List<Issue> issues) { }
    @Schema(name = "ExamCheckPath")
    public record Path(String pathId, List<Choice> choices, String result, List<String> partIds,
                       int totalQuestions, int totalMinutes, BigDecimal totalPoints, List<Block> blocks,
                       List<Subject> subjects, List<Selection> selections, List<Issue> issues) { }
    @Schema(name = "ExamCheckResult")
    public record Result(UUID examId, Instant checkedAt, double durationSeconds, long timeLimitSeconds,
                         boolean stale, String invalidReason, String status, int issueCount, List<Path> paths,
                         List<Group> groups, int partCount, BigDecimal totalPoints, int printedQuestions,
                         BigDecimal maxScore, @Schema(types = {"array", "null"}) List<SectionState> sectionStates) { }
    @Schema(name = "ExamCheckRun")
    public record Run(UUID runId, String state, Result result, String errorCode) { }
}
