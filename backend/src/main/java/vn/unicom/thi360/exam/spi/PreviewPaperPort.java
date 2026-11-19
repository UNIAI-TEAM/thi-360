package vn.unicom.thi360.exam.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads a generated trial paper back from the short-lived preview store (decision D-b, BS 9). The paper is already
 * ordered by {@link PaperOrdering}: the port never solves again and never rereads the draft, the template or the
 * question bank, so every call for the same {@code previewId} returns the same paper.
 *
 * <p>A preview that is missing, expired, saved by an older release without the trial fields, or that belongs to
 * another exam is reported as {@link PreviewGoneException} (HTTP 410 {@code PREVIEW_EXPIRED}); the port never
 * confirms that a preview of another exam exists.
 */
public interface PreviewPaperPort {

    /** The ordered paper of {@code previewId}. */
    PreviewPaper load(UUID examId, UUID previewId);

    /**
     * Raises the expiry of the preview to {@code max(current expiry, now + paper duration + 30 minutes)} in one
     * atomic step and returns the new expiry. Repeated or concurrent calls never shorten an expiry already granted.
     * A preview never extended keeps its 30 minutes.
     */
    Instant extendForTrial(UUID examId, UUID previewId);

    /** Same meaning as the exam module's {@code PREVIEW_EXPIRED}; the consumer maps it to 410. */
    class PreviewGoneException extends vn.unicom.thi360.exam.application.PreviewExpiredException {
    }

    /**
     * @param versionNo      published version the paper was generated from, null for a draft preview
     * @param kindCode       exam kind code captured at generation, null when the exam has no kind
     * @param timing         {@code SHARED} or {@code PER_PART}
     * @param durationMinutes exam duration captured at generation ({@code SHARED}: the exam's own duration;
     *                       {@code PER_PART}: the sum of the generated parts)
     * @param parts          in paper order
     */
    record PreviewPaper(UUID examId, Integer versionNo, long seed, String kindCode, String timing,
                        int durationMinutes, List<Part> parts) {
    }

    record Part(String partId, String name, Integer order, Integer durationMinutes, int firstNumber, int lastNumber,
                BigDecimal points, List<Block> blocks, List<Segment> displaySegments) {
    }

    /** Display segment declared by the part (H40); positions count from 1 inside the part. */
    record Segment(String code, String name, Integer fromPosition, Integer toPosition, Integer commandCount,
                   BigDecimal maxPoints) {
    }

    record Block(String blockId, String name, String subjectCode, String questionType, boolean manualGrading,
                 BigDecimal pointsEach, int firstNumber, int lastNumber, int questionCount, List<Item> items) {
    }

    /** One entry of a block, in presentation order: a single question or a whole question set. */
    record Item(Kind kind, Question question, QuestionSet set) {
        public enum Kind { QUESTION, SET }
    }

    /**
     * A question set (chùm): the root question with the files of the passage, and its children in order.
     * {@code versionNo} applies to the root and to every child.
     */
    record QuestionSet(UUID questionId, String code, int versionNo, List<Attachment> attachments,
                       List<Question> children) {
    }

    /**
     * A numbered question. {@code number} is the first position it occupies; a cloze takes one position per blank.
     * {@code optionOrder} is the displayed order of option ids, null when the type has none.
     * {@code attachments} are the files of a single question; a child of a set carries none.
     */
    record Question(int number, UUID questionId, String code, int versionNo, String type, BigDecimal points,
                    List<String> optionOrder, List<Attachment> attachments) {
    }

    record Attachment(UUID id, String name, String mime) {
    }
}
