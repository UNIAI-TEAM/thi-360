package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/** Quotas shared by all slot groups belonging to the same parent block. */
public record SlotParentQuota(String parentGroupId, MatrixQuota matrix,
        List<CompetencyQuota> competencies, List<MapidGroupQuota> mapidGroups) {
    public SlotParentQuota {
        Objects.requireNonNull(parentGroupId, "parentGroupId must not be null");
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        mapidGroups = mapidGroups == null ? List.of() : List.copyOf(mapidGroups);
    }
}
