package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đề làm thử dành cho admin đọc từ snapshot xem trước — đã lọc sạch đáp án, hướng dẫn chấm và metadata
 * ngân hàng câu hỏi, giữ nguyên thứ tự phần/khối/câu/đoạn/âm thanh và thứ tự đáp án xáo (AC-18, AC-24, F17).
 */
@Schema(name = "PreviewTrialPaperResponse")
public record PreviewTrialPaperResponse(
        UUID examId,
        UUID previewId,
        Integer versionNo,
        String kindCode,
        String timing,
        int durationMinutes,
        List<PreviewPaperPartView> parts) {

    @Schema(name = "PreviewTrialPart")
    public record PreviewPaperPartView(
            String partId,
            String name,
            Integer order,
            Integer durationMinutes,
            int firstNumber,
            int lastNumber,
            BigDecimal points,
            List<PreviewPaperSegmentView> displaySegments,
            List<PreviewPaperBlockView> blocks) {}

    @Schema(name = "PreviewTrialSegment")
    public record PreviewPaperSegmentView(
            String code,
            String name,
            Integer fromPosition,
            Integer toPosition,
            Integer commandCount,
            BigDecimal maxPoints) {}

    @Schema(name = "PreviewTrialBlock")
    public record PreviewPaperBlockView(
            String blockId,
            String name,
            String subjectCode,
            String questionType,
            boolean manualGrading,
            BigDecimal pointsEach,
            int firstNumber,
            int lastNumber,
            int questionCount,
            List<PreviewPaperItemView> items) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "PreviewTrialItem")
    public record PreviewPaperItemView(
            String kind, PreviewPaperQuestionView question, PreviewPaperQuestionSetView set) {}

    @Schema(name = "PreviewTrialQuestionSet")
    public record PreviewPaperQuestionSetView(
            UUID questionId,
            String code,
            int versionNo,
            String content,
            String passage,
            Integer blankNumberStart,
            List<PreviewPaperAttachmentView> attachments,
            List<PreviewPaperQuestionView> children) {}

    @Schema(name = "PreviewTrialQuestion")
    public record PreviewPaperQuestionView(
            int number,
            UUID questionId,
            String code,
            int versionNo,
            QuestionType type,
            String content,
            BigDecimal points,
            List<String> optionOrder,
            List<PreviewPaperAttachmentView> attachments,
            AttemptQuestionBody body) {}

    @Schema(name = "PreviewTrialAttachment")
    public record PreviewPaperAttachmentView(UUID id, String name, String mime, String downloadUrl) {}
}
