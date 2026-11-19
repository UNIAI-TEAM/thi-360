package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/** Quotas shared by all slot groups belonging to the same parent block. */
public record SlotParentQuota(String parentGroupId, MatrixQuota matrix,
        List<CompetencyQuota> competencies, List<MapidGroupQuota> mapidGroups,
        List<TopicQuota> topicQuotas) {
    public SlotParentQuota(String parentGroupId, MatrixQuota matrix,
            List<CompetencyQuota> competencies, List<MapidGroupQuota> mapidGroups) {
        this(parentGroupId, matrix, competencies, mapidGroups, List.of());
    }
    public SlotParentQuota {
        Objects.requireNonNull(parentGroupId, "parentGroupId must not be null");
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        mapidGroups = mapidGroups == null ? List.of() : List.copyOf(mapidGroups);
        topicQuotas = topicQuotas == null ? List.of() : List.copyOf(topicQuotas);
    }
}
