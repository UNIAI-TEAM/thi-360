package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Một đơn vị đếm vào ma trận của một ứng viên (spec 3, 4.3).
 *
 * <p>Câu độc lập có đúng một đơn vị. Bộ câu hỏi đếm theo câu con ({@code childId} là id câu con),
 * Điền khuyết đếm theo vị trí ({@code childId} = null). Mỗi đơn vị mang phân loại riêng; các trường
 * phân loại để null nghĩa là không gắn giá trị đó (không khớp chỉ tiêu tương ứng).
 *
 * @param childId         id câu con khi đơn vị là câu con của bộ; null với câu độc lập và vị trí Điền khuyết
 * @param gradeCode       mã khối lớp, null nếu không gắn
 * @param difficultyCode  mã độ khó, null nếu không gắn
 * @param mapidIds        id các nút MAPID (kiến thức) mà đơn vị gắn; rỗng nếu không gắn
 * @param competencyCodes mã năng lực; rỗng nếu không gắn
 * @param points          điểm của đơn vị (điểm câu con / điểm vị trí); null nếu không xét điểm
 */
public record CountingUnit(
        String childId,
        String gradeCode,
        String difficultyCode,
        List<String> mapidIds,
        List<String> competencyCodes,
        BigDecimal points) {

    public CountingUnit {
        mapidIds = mapidIds == null ? List.of() : List.copyOf(mapidIds);
        competencyCodes = competencyCodes == null ? List.of() : List.copyOf(competencyCodes);
    }

    /** Đơn vị đơn giản của câu độc lập: chỉ có khối lớp, độ khó và điểm. */
    public static CountingUnit standalone(String gradeCode, String difficultyCode, BigDecimal points) {
        return new CountingUnit(null, gradeCode, difficultyCode, List.of(), List.of(), points);
    }
}
