package vn.unicom.thi360.questionbank.domain.importing;

import java.util.UUID;

/**
 * Một nút cây kiến thức (MAPID) mà dòng nhập tham chiếu bằng mã. Chỉ nút cấp Kiến thức
 * ({@code leaf}) được gắn vào câu (spec 4.1: "Chỉ gắn nút cấp Kiến thức"); nút khác môn của
 * dòng không khớp.
 *
 * @param nodeId id giá trị danh mục của nút
 * @param code mã MAPID
 * @param subjectCode mã môn mà nút thuộc về (khớp {@code subject} của dòng)
 * @param leaf {@code true} khi nút là cấp Kiến thức (cấp được phép gắn)
 * @param active {@code false} = đã ngừng, coi như không có
 */
public record ImportKnowledgeNode(UUID nodeId, String code, String subjectCode, boolean leaf, boolean active) {}
