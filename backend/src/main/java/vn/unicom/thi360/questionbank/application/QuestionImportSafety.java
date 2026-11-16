package vn.unicom.thi360.questionbank.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.questionbank.domain.QuestionFieldError;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.domain.importing.ImportQuestionDraft;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/** Checks the same HTML and save-level constraints as the question save path, without writing. */
@Component
public class QuestionImportSafety {
    private final HtmlContentSanitizer sanitizer;
    private final QuestionAssetResolver assets;

    public QuestionImportSafety(HtmlContentSanitizer sanitizer, QuestionAssetResolver assets) {
        this.sanitizer = sanitizer;
        this.assets = assets;
    }

    /** Inspect even invalid rows, whose partially parsed HTML can still reach preview. */
    List<QuestionFieldError> htmlErrors(ImportQuestionDraft draft) {
        List<QuestionFieldError> errors = new ArrayList<>();
        html(draft.content(), "content", errors);
        html(draft.solution(), "solution", errors);
        for (int i = 0; i < draft.commonMistakes().size(); i++) {
            html(draft.commonMistakes().get(i), "commonMistakes[" + i + "]", errors);
        }
        body(draft.body(), "body", errors);
        return errors;
    }

    public List<QuestionFieldError> validate(SaveQuestionRequest request) {
        BigDecimal points = switch (request.body()) {
            case QuestionBody.ClozeBody b -> b.blanks().stream()
                    .map(blank -> blank.points() == null ? BigDecimal.ONE : blank.points())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case QuestionBody.QuestionSetBody b -> b.children().stream()
                    .map(child -> child.points() == null ? BigDecimal.ONE : child.points())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case null, default -> request.points() == null ? BigDecimal.ONE : request.points();
        };
        return QuestionBodyValidator.validateSave(new QuestionInput(request.type(), request.body(), points,
                request.content(), request.solution(), request.commonMistakes(), request.attachmentIds()), assets);
    }

    private void html(String text, String path, List<QuestionFieldError> errors) {
        if (text != null && !sanitizer.sanitize(text, HtmlContentSanitizer.Options.textOnly()).isClean()) {
            errors.add(new QuestionFieldError("QUESTION_CONTENT_UNSAFE", path, "QUESTION_CONTENT_UNSAFE"));
        }
    }

    private void body(QuestionBody value, String path, List<QuestionFieldError> errors) {
        switch (value) {
            case null -> { }
            case QuestionBody.SingleChoiceBody b -> b.options().forEach(o -> html(o.content(), path + ".options", errors));
            case QuestionBody.MultipleChoiceBody b -> b.options().forEach(o -> html(o.content(), path + ".options", errors));
            case QuestionBody.ShortAnswerBody b -> html(b.inputHint(), path + ".inputHint", errors);
            case QuestionBody.ClozeBody b -> html(b.passage(), path + ".passage", errors);
            case QuestionBody.QuestionSetBody b -> {
                html(b.passage(), path + ".passage", errors);
                for (int i = 0; i < b.children().size(); i++) {
                    var child = b.children().get(i);
                    String childPath = path + ".children[" + i + "]";
                    html(child.content(), childPath + ".content", errors);
                    html(child.solution(), childPath + ".solution", errors);
                    body(child.body(), childPath + ".body", errors);
                }
            }
            case QuestionBody.TrueFalseBody b -> b.statements().forEach(s -> html(s.content(), path + ".statements", errors));
            case QuestionBody.MatchingBody b -> {
                b.left().forEach(s -> html(s.content(), path + ".left", errors));
                b.right().forEach(s -> html(s.content(), path + ".right", errors));
            }
            case QuestionBody.EssayBody b -> {
                html(b.gradingGuide(), path + ".gradingGuide", errors);
                html(b.sampleAnswer(), path + ".sampleAnswer", errors);
                b.rubric().forEach(c -> html(c.description(), path + ".rubric", errors));
            }
        }
    }
}
