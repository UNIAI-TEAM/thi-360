package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Một đơn vị đếm của một ứng viên (spec 3, 4.3): thành phần đóng góp vào tổng số câu và vào mọi
 * chỉ tiêu của nhóm lẫn của môn cùng một lúc (B9).
 *
 * <p>Câu độc lập có đúng một đơn vị. Bộ câu hỏi đếm theo câu con ({@code childId} là id câu con),
 * Điền khuyết đếm theo vị trí ({@code childId} = null). Mỗi đơn vị mang phân loại riêng; các
 * trường phân loại để null nghĩa là không gắn giá trị đó (không khớp chỉ tiêu tương ứng).
 *
 * @param childId         id câu con khi đơn vị là câu con của bộ; null với câu độc lập và vị trí Điền khuyết
 * @param answerType      dạng trả lời của đơn vị theo D22 (mã dạng câu: đơn vị của câu độc lập lấy
 *                        dạng của câu, câu con lấy dạng của câu con, vị trí Điền khuyết là CLOZE)
 * @param gradeCode       mã khối lớp, null nếu không gắn
 * @param difficultyCode  mã độ khó, null nếu không gắn
 * @param mapidIds        id các nút MAPID (kiến thức) mà đơn vị gắn; rỗng nếu không gắn
 * @param competencyCodes mã năng lực; rỗng nếu không gắn
 * @param points          điểm của đơn vị (điểm câu con / điểm vị trí); null nếu không xét điểm
 */
public record CountingUnit(
        String childId,
        String answerType,
        String gradeCode,
        String difficultyCode,
        List<String> mapidIds,
        List<String> competencyCodes,
        BigDecimal points) {

    public CountingUnit {
        Objects.requireNonNull(answerType, "answerType must not be null (D22)");
        mapidIds = mapidIds == null ? List.of() : List.copyOf(mapidIds);
        competencyCodes = competencyCodes == null ? List.of() : List.copyOf(competencyCodes);
    }

    /** Đơn vị đơn giản của câu độc lập: dạng trả lời, khối lớp, độ khó và điểm. */
    public static CountingUnit standalone(String answerType, String gradeCode, String difficultyCode,
            BigDecimal points) {
        return new CountingUnit(null, answerType, gradeCode, difficultyCode, List.of(), List.of(),
                points);
    }
}
