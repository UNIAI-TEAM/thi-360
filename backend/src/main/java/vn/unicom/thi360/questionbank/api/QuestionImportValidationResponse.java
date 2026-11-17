package vn.unicom.thi360.questionbank.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.questionbank.application.QuestionImportValidationService;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.importing.ExistingQuestionRef;
import vn.unicom.thi360.questionbank.domain.importing.ImportQuestionDraft;
import vn.unicom.thi360.questionbank.domain.importing.ImportRowStatus;
import vn.unicom.thi360.questionbank.domain.importing.ImportSummary;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Public preview preserves the web import draft fields and includes the full typed body. */
public record QuestionImportValidationResponse(UUID uploadId, List<Row> rows, ImportSummary summary) {
    public record Row(int rowNumber, String sourceCode, ImportRowStatus status,
            List<QuestionImportValidationService.Issue> issues, Draft draft, ExistingQuestionRef duplicateOf,
            boolean isSetPassage, Integer parentRowNumber) {}
    public record Option(String letter, String content, Boolean correct) {}
    public record Statement(String letter, String content, Boolean answer) {}
    public record Criterion(String description, BigDecimal points) {}
    public record Draft(QuestionType type, String content, List<Option> options, List<Statement> statements,
            List<String> acceptedAnswers, BigDecimal points, String inputHint, String gradingGuide,
            List<Criterion> rubric, String sampleAnswer, String solution, String subjectCode, String gradeCode,
            String difficultyCode, List<KnowledgeRef> knowledge, List<String> tagCodes, String setCode,
            QuestionBody body, String taskTypeCode, List<String> commonMistakes) {}

    public static QuestionImportValidationResponse from(QuestionImportValidationService.Validation validation) {
        return new QuestionImportValidationResponse(validation.uploadId(), validation.rows().stream()
                .map(row -> new Row(row.rowNumber(), row.sourceCode(), row.status(), row.issues(),
                        draft(row.draft(), validation.knowledgeNames()), row.duplicateOf(), row.isSetPassage(), row.parentRowNumber())).toList(),
                validation.summary());
    }

    private static Draft draft(ImportQuestionDraft source, Map<UUID, String> knowledgeNames) {
        if (source == null) return null;
        List<Option> options = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();
        List<String> answers = List.of();
        List<Criterion> rubric = List.of();
        String inputHint = null, gradingGuide = null, sampleAnswer = null;
        if (source.body() instanceof QuestionBody.SingleChoiceBody b) {
            addOptions(options, b.options());
        } else if (source.body() instanceof QuestionBody.MultipleChoiceBody b) {
            addOptions(options, b.options());
        } else if (source.body() instanceof QuestionBody.ShortAnswerBody b) {
            answers = b.answers();
            inputHint = b.inputHint();
        } else if (source.body() instanceof QuestionBody.TrueFalseBody b) {
            for (int i = 0; i < b.statements().size(); i++) {
                var s = b.statements().get(i);
                statements.add(new Statement(Character.toString('A' + i), s.content(), s.answer()));
            }
        } else if (source.body() instanceof QuestionBody.EssayBody b) {
            gradingGuide = b.gradingGuide();
            sampleAnswer = b.sampleAnswer();
            rubric = b.rubric().stream().map(c -> new Criterion(c.description(), c.points())).toList();
        }
        List<KnowledgeRef> knowledge = source.knowledge().stream()
                .map(k -> new KnowledgeRef(k.nodeId(), k.code(), knowledgeNames.get(k.nodeId()), k.primary())).toList();
        return new Draft(source.type(), source.content(), List.copyOf(options), List.copyOf(statements), answers,
                source.points(), inputHint, gradingGuide, rubric, sampleAnswer, source.solution(), source.subjectCode(),
                source.gradeCode(), source.difficultyCode(), knowledge, source.tagCodes(), source.setCode(),
                source.body(), source.taskTypeCode(), source.commonMistakes());
    }

    private static void addOptions(List<Option> output, List<QuestionBody.ChoiceOption> options) {
        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            String id = option.id();
            String letter = id != null && id.startsWith("opt-") ? id.substring(4) : Character.toString('A' + i);
            output.add(new Option(letter, option.content(), option.correct()));
        }
    }
}
