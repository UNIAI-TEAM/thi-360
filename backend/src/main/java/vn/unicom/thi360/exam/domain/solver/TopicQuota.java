package vn.unicom.thi360.exam.domain.solver;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A minimum topic assignment within one section or one group.
 *
 * <p>Pre-indexes {@code nodeIds}, {@code gradeCodes}, {@code difficultyCodes}, and {@code groupIds} into immutable
 * {@link Set}s so {@link #matches(String, CountingUnit)} runs in O(|unit.mapidIds|) without stream allocation or linear
 * scans over expanded knowledge subtrees. Value equality, JSON serialization, and accessors remain identical to the
 * canonical record form.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class TopicQuota {
    private final String id;
    private final List<String> nodeIds;
    private final int count;
    private final List<String> gradeCodes;
    private final List<String> difficultyCodes;
    private final List<String> groupIds;
    private final boolean mapid;

    private final Set<String> nodeSet;
    private final Set<String> gradeSet;
    private final Set<String> difficultySet;
    private final Set<String> groupSet;

    @JsonCreator
    public TopicQuota(
            @JsonProperty("id") String id,
            @JsonProperty("nodeIds") List<String> nodeIds,
            @JsonProperty("count") int count,
            @JsonProperty("gradeCodes") List<String> gradeCodes,
            @JsonProperty("difficultyCodes") List<String> difficultyCodes,
            @JsonProperty("groupIds") List<String> groupIds,
            @JsonProperty("mapid") boolean mapid) {
        Objects.requireNonNull(id, "id must not be null");
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        this.id = id;
        this.nodeIds = List.copyOf(nodeIds);
        this.count = count;
        this.gradeCodes = List.copyOf(gradeCodes);
        this.difficultyCodes = List.copyOf(difficultyCodes);
        this.groupIds = List.copyOf(groupIds);
        this.mapid = mapid;
        this.nodeSet = Set.copyOf(this.nodeIds);
        this.gradeSet = Set.copyOf(this.gradeCodes);
        this.difficultySet = Set.copyOf(this.difficultyCodes);
        this.groupSet = Set.copyOf(this.groupIds);
    }

    public TopicQuota(String id, List<String> nodeIds, int count, List<String> gradeCodes,
            List<String> difficultyCodes, List<String> groupIds) {
        this(id, nodeIds, count, gradeCodes, difficultyCodes, groupIds, false);
    }

    @JsonProperty("id")
    public String id() { return id; }

    @JsonProperty("nodeIds")
    public List<String> nodeIds() { return nodeIds; }

    @JsonProperty("count")
    public int count() { return count; }

    @JsonProperty("gradeCodes")
    public List<String> gradeCodes() { return gradeCodes; }

    @JsonProperty("difficultyCodes")
    public List<String> difficultyCodes() { return difficultyCodes; }

    @JsonProperty("groupIds")
    public List<String> groupIds() { return groupIds; }

    @JsonProperty("mapid")
    public boolean mapid() { return mapid; }

    boolean matches(String groupId, CountingUnit unit) {
        if (!groupSet.isEmpty() && (groupId == null || !groupSet.contains(groupId))) return false;
        if (!gradeSet.isEmpty() && (unit.gradeCode() == null || !gradeSet.contains(unit.gradeCode()))) return false;
        if (!difficultySet.isEmpty()
                && (unit.difficultyCode() == null || !difficultySet.contains(unit.difficultyCode()))) return false;
        if (nodeSet.isEmpty()) return false;
        for (String mapidId : unit.mapidIds()) {
            if (nodeSet.contains(mapidId)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopicQuota that)) return false;
        return count == that.count
                && mapid == that.mapid
                && id.equals(that.id)
                && nodeIds.equals(that.nodeIds)
                && gradeCodes.equals(that.gradeCodes)
                && difficultyCodes.equals(that.difficultyCodes)
                && groupIds.equals(that.groupIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nodeIds, count, gradeCodes, difficultyCodes, groupIds, mapid);
    }

    @Override
    public String toString() {
        return "TopicQuota[id=" + id + ", nodeIds=" + nodeIds + ", count=" + count
                + ", gradeCodes=" + gradeCodes + ", difficultyCodes=" + difficultyCodes
                + ", groupIds=" + groupIds + ", mapid=" + mapid + "]";
    }
}
