package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/** A minimum topic assignment within one section or one group. */
public record TopicQuota(String id, List<String> nodeIds, int count, List<String> gradeCodes,
        List<String> difficultyCodes, List<String> groupIds) {
    public TopicQuota {
        Objects.requireNonNull(id, "id must not be null");
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        nodeIds = List.copyOf(nodeIds);
        gradeCodes = List.copyOf(gradeCodes);
        difficultyCodes = List.copyOf(difficultyCodes);
        groupIds = List.copyOf(groupIds);
    }

    boolean matches(String groupId, CountingUnit unit) {
        return (groupIds.isEmpty() || groupIds.contains(groupId))
                && (gradeCodes.isEmpty() || (unit.gradeCode() != null && gradeCodes.contains(unit.gradeCode())))
                && (difficultyCodes.isEmpty() || (unit.difficultyCode() != null && difficultyCodes.contains(unit.difficultyCode())))
                && unit.mapidIds().stream().anyMatch(nodeIds::contains);
    }
}
