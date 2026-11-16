package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Một chỉ tiêu nội bộ của khối, đã dịch từ {@link BlockQuota}.
 *
 * <p>Một {@link CountingUnit} khớp chỉ tiêu này khi {@code matcher} đúng với nó; một đơn vị được tính vào
 * mọi chỉ tiêu nó khớp (ví dụ SEPARATE: một đơn vị khớp cả chỉ tiêu lớp và chỉ tiêu độ khó).
 *
 * @param kind      loại chỉ tiêu, dùng để chẩn đoán và nhận biết chiều chính
 * @param key       khóa định danh (mã ô / mã chiều / mã năng lực / khóa nhóm MAPID)
 * @param quota     số đơn vị đếm phải khớp
 * @param matcher   vị từ khớp trên một đơn vị đếm
 */
record Requirement(Kind kind, String key, int quota, Predicate<CountingUnit> matcher) {

    enum Kind {
        COMBINED_CELL, SEPARATE_GRADE, SEPARATE_DIFFICULTY, COMPETENCY, MAPID_GROUP
    }

    static Requirement combinedCell(CellQuota cell) {
        return new Requirement(Kind.COMBINED_CELL, cell.gradeCode() + "/" + cell.difficultyCode(),
                cell.quota(),
                u -> cell.gradeCode().equals(u.gradeCode()) && cell.difficultyCode().equals(u.difficultyCode()));
    }

    static Requirement separateGrade(DimensionQuota dim) {
        return new Requirement(Kind.SEPARATE_GRADE, dim.code(), dim.quota(),
                u -> dim.code().equals(u.gradeCode()));
    }

    static Requirement separateDifficulty(DimensionQuota dim) {
        return new Requirement(Kind.SEPARATE_DIFFICULTY, dim.code(), dim.quota(),
                u -> dim.code().equals(u.difficultyCode()));
    }

    static Requirement competency(CompetencyQuota comp) {
        return new Requirement(Kind.COMPETENCY, comp.competencyCode(), comp.quota(),
                u -> u.competencyCodes().contains(comp.competencyCode()));
    }

    static Requirement mapidGroup(MapidGroupQuota group) {
        Set<String> nodes = Set.copyOf(group.nodeIds());
        return new Requirement(Kind.MAPID_GROUP, group.groupKey(), group.quota(),
                u -> {
                    List<String> ids = u.mapidIds();
                    for (String id : ids) {
                        if (nodes.contains(id)) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
