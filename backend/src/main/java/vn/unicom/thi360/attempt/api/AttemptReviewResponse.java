package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(
        name = "AttemptReviewResponse",
        description = "Xem lại bài làm theo mức result_view (RIGHT_WRONG hoặc FULL_SOLUTION, LT §10.2)")
public record AttemptReviewResponse(
        UUID attemptId,
        AttemptState state,
        String resultView,
        String selectedPartCode,
        List<ReviewPartView> parts,
        List<ReviewItemView> items) {

    @Schema(name = "AttemptReviewPart")
    public record ReviewPartView(
            UUID partId,
            String partCode,
            String partName,
            int ord,
            String state,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxScore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AttemptReviewItem")
    public record ReviewItemView(
            UUID itemId,
            UUID partId,
            String partCode,
            UUID parentItemId,
            Integer displayNo,
            int ord,
            int unitCount,
            String sectionId,
            String componentCode,
            QuestionType questionType,
            String content,
            List<ReviewAttachmentView> attachments,
            ReviewQuestionBody body,
            AnswerValue studentAnswer,
            List<String> optionOrder,
            BigDecimal pointsMax,
            BigDecimal earnedPoints,
            ItemOutcome outcome,
            boolean flagged,
            Map<String, Object> gradingDetail,
            ReviewExpectedAnswer expectedAnswer,
            String solution,
            ReviewEssayFeedback essayFeedback) {}

    @Schema(name = "AttemptReviewAttachment")
    public record ReviewAttachmentView(UUID id, String name, String mime, String downloadUrl) {}

    @Schema(name = "AttemptReviewQuestionBody")
    public record ReviewQuestionBody(
            String passage,
            Integer blankNumberStart,
            List<ReviewOptionView> options,
            String scoringRule,
            String inputHint,
            String answerFormat,
            List<ReviewClozeBlankView> blanks,
            List<ReviewStatementView> statements,
            List<ReviewMatchingSideView> left,
            List<ReviewMatchingSideView> right,
            Boolean reuseRight,
            Integer wordLimit) {}

    @Schema(name = "AttemptReviewOption")
    public record ReviewOptionView(String id, String content) {}

    @Schema(name = "AttemptReviewClozeBlank")
    public record ReviewClozeBlankView(String id) {}

    @Schema(name = "AttemptReviewStatement")
    public record ReviewStatementView(String id, String content) {}

    @Schema(name = "AttemptReviewMatchingSide")
    public record ReviewMatchingSideView(String id, String content) {}

    @Schema(name = "AttemptReviewExpectedAnswer")
    public record ReviewExpectedAnswer(
            List<String> expectedOptionIds,
            List<String> acceptedTexts,
            List<ReviewClozeKey> clozeKeys,
            List<ReviewTrueFalseKey> trueFalseKeys,
            List<ReviewMatchingKey> matchingKeys) {}

    @Schema(name = "AttemptReviewClozeKey")
    public record ReviewClozeKey(String blankId, List<String> acceptedTexts) {}

    @Schema(name = "AttemptReviewTrueFalseKey")
    public record ReviewTrueFalseKey(String statementId, Boolean expected) {}

    @Schema(name = "AttemptReviewMatchingKey")
    public record ReviewMatchingKey(String leftId, String rightId) {}

    @Schema(name = "AttemptReviewEssayFeedback")
    public record ReviewEssayFeedback(
            BigDecimal points,
            String comment,
            List<ReviewEssayCriterionScore> criteria) {}

    @Schema(name = "AttemptReviewEssayCriterionScore")
    public record ReviewEssayCriterionScore(String id, BigDecimal points, String comment) {}
}
