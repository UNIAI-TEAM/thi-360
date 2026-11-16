package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;
import java.util.UUID;

/**
 * Một nút cây kiến thức (MAPID) mà dòng nhập tham chiếu bằng mã. Chỉ nút cấp Kiến thức
 * ({@code leaf}) được gắn vào câu (spec 4.1: "Chỉ gắn nút cấp Kiến thức"). Một nút có thể
 * thuộc nhiều môn — chương được liên kết tới nhiều SUBJECT trong danh mục — nên kiểm tra môn
 * của dòng khớp BẤT KỲ mã nào trong {@link #subjectCodes}, không phải chỉ mã đầu tiên.
 *
 * @param nodeId id giá trị danh mục của nút
 * @param code mã MAPID
 * @param subjectCodes các mã môn mà nút thuộc về (khớp {@code subject} của dòng)
 * @param leaf {@code true} khi nút là cấp Kiến thức (cấp được phép gắn)
 * @param active {@code false} = đã ngừng, coi như không có
 */
public record ImportKnowledgeNode(
    UUID nodeId, String code, List<String> subjectCodes, boolean leaf, boolean active) {

    public ImportKnowledgeNode {
        subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
    }
}
