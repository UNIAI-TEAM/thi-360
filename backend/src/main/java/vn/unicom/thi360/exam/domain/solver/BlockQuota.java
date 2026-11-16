package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chỉ tiêu của một khối trong cấu trúc bài thi (mock {@code QuestionBlock}, spec 5.4).
 *
 * <p>Khối lấy đúng {@code questionCount} đơn vị đếm. Với khối câu chùm, {@code setCount} × {@code setSize}
 * = {@code questionCount} và chỉ ứng viên dạng bộ / Điền khuyết có đúng {@code setSize} đơn vị mới hợp lệ.
 *
 * <p>{@code requiredPointsPerUnit} là điều kiện lọc nguồn (B5): chỉ đơn vị có {@code points} bằng giá trị
 * này mới được chọn. {@code expectedScoringRule} so với {@code scoringRule} của ứng viên; null = không so.
 *
 * @param blockId                id khối, duy nhất trên một đường
 * @param questionCount          số đơn vị đếm của khối
 * @param setCount               số bộ cần chọn, hoặc null khi khối không phải khối chùm
 * @param setSize                số đơn vị mỗi bộ, hoặc null
 * @param matrix                 chỉ tiêu khối lớp × độ khó
 * @param competencies           chỉ tiêu năng lực bổ sung, rỗng khi không dùng
 * @param mapidGroups            chỉ tiêu nhóm MAPID bổ sung, rỗng khi không dùng
 * @param requiredPointsPerUnit  điểm bắt buộc của từng đơn vị, null khi không lọc
 * @param expectedScoringRule    quy tắc chấm mong đợi của khối, null khi không so
 */
public record BlockQuota(
        String blockId,
        int questionCount,
        Integer setCount,
        Integer setSize,
        MatrixQuota matrix,
        List<CompetencyQuota> competencies,
        List<MapidGroupQuota> mapidGroups,
        BigDecimal requiredPointsPerUnit,
        String expectedScoringRule) {

    public BlockQuota {
        Objects.requireNonNull(blockId, "blockId must not be null");
        Objects.requireNonNull(matrix, "matrix must not be null");
        if (questionCount < 0) {
            throw new IllegalArgumentException("questionCount must not be negative");
        }
        if ((setCount == null) != (setSize == null)) {
            throw new IllegalArgumentException("setCount and setSize must be both null or both set");
        }
        if (setCount != null) {
            if (setCount <= 0 || setSize <= 0) {
                throw new IllegalArgumentException("setCount and setSize must be positive");
            }
            if ((long) setCount * setSize != questionCount) {
                throw new IllegalArgumentException(
                        "setCount x setSize must equal questionCount for a set block");
            }
        }
        if (requiredPointsPerUnit != null && requiredPointsPerUnit.signum() <= 0) {
            throw new IllegalArgumentException("requiredPointsPerUnit must be positive");
        }
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        mapidGroups = mapidGroups == null ? List.of() : List.copyOf(mapidGroups);
        Set<String> seen = new HashSet<>();
        for (CompetencyQuota c : competencies) {
            if (!seen.add("C" + c.competencyCode())) {
                throw new IllegalArgumentException("duplicate competency quota " + c.competencyCode());
            }
        }
        for (MapidGroupQuota g : mapidGroups) {
            if (!seen.add("M" + g.groupKey())) {
                throw new IllegalArgumentException("duplicate MAPID group quota " + g.groupKey());
            }
        }
    }

    /** Khối đơn giản chỉ có ma trận COMBINED. */
    public static BlockQuota combined(String blockId, int questionCount, List<CellQuota> cells) {
        return new BlockQuota(blockId, questionCount, null, null, new MatrixQuota.Combined(cells),
                List.of(), List.of(), null, null);
    }

    /** Khối đơn giản chỉ có ma trận SEPARATE. */
    public static BlockQuota separate(String blockId, int questionCount, List<DimensionQuota> grades,
            List<DimensionQuota> difficulties) {
        return new BlockQuota(blockId, questionCount, null, null,
                new MatrixQuota.Separate(grades, difficulties), List.of(), List.of(), null, null);
    }

    /** Khối câu chùm. */
    public static BlockQuota setBlock(String blockId, int setCount, int setSize, MatrixQuota matrix) {
        return new BlockQuota(blockId, setCount * setSize, setCount, setSize, matrix,
                List.of(), List.of(), null, null);
    }
}
