package vn.unicom.thi360.questionbank.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Nội dung một phiên bản câu hỏi, lưu ở {@code question_version.snapshot} (jsonb, spec 4.1):
 * phân loại (id giá trị danh mục), điểm, nội dung HTML đã lọc, thân câu, lời giải, lỗi sai
 * thường gặp, tệp đính kèm, kiến thức và nhãn.
 *
 * <p>Với câu {@code QUESTION_SET}, {@code body} là {@code QuestionSetBody} có
 * {@code children[]} lồng (mỗi phần tử mang {@code id} bản ghi câu con); câu con còn có dòng
 * {@code question_version} riêng cùng {@code version_no} với bản này (snapshot của câu con đã
 * quy hồi phân loại kế thừa tại thời điểm ghi).
 */
public record QuestionSnapshot(
    UUID subjectItemId,
    UUID gradeItemId,
    UUID levelItemId,
    UUID taskTypeItemId,
    UUID competencyItemId,
    BigDecimal points,
    List<KnowledgeRef> knowledge,
    List<UUID> tagItemIds,
    String content,
    String solution,
    List<String> commonMistakes,
    List<UUID> attachmentIds,
    QuestionBody body,
    String sourceCode) {

    public QuestionSnapshot {
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        tagItemIds = tagItemIds == null ? List.of() : List.copyOf(tagItemIds);
        commonMistakes = commonMistakes == null ? List.of() : List.copyOf(commonMistakes);
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }

    /** Một nút kiến thức gắn với phiên bản; đúng một nút {@code primary} khi có ít nhất một. */
    public record KnowledgeRef(UUID itemId, boolean primary) {}
}
