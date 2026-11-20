package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * A minimum topic assignment within one section or one group.
 *
 * @param mapid the minimum was compiled from a MAPID rule of a {@code mapidMode MATRIX} section
 *              (hard, by construction); a declared/legacy quota keeps {@code false}
 */
public record TopicQuota(String id, List<String> nodeIds, int count, List<String> gradeCodes,
        List<String> difficultyCodes, List<String> groupIds, boolean mapid) {
    public TopicQuota {
        Objects.requireNonNull(id, "id must not be null");
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        nodeIds = List.copyOf(nodeIds);
        gradeCodes = List.copyOf(gradeCodes);
        difficultyCodes = List.copyOf(difficultyCodes);
        groupIds = List.copyOf(groupIds);
    }

    public TopicQuota(String id, List<String> nodeIds, int count, List<String> gradeCodes,
            List<String> difficultyCodes, List<String> groupIds) {
        this(id, nodeIds, count, gradeCodes, difficultyCodes, groupIds, false);
    }

    boolean matches(String groupId, CountingUnit unit) {
        return (groupIds.isEmpty() || groupIds.contains(groupId))
                && (gradeCodes.isEmpty() || (unit.gradeCode() != null && gradeCodes.contains(unit.gradeCode())))
                && (difficultyCodes.isEmpty() || (unit.difficultyCode() != null && difficultyCodes.contains(unit.difficultyCode())))
                && unit.mapidIds().stream().anyMatch(nodeIds::contains);
    }
}
