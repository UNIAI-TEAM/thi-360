package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * An upper bound on topic units (UNI-2758 rules 2 and 3-exact): at most {@code max} selected counting units carry
 * a node of {@code nodeIds}. {@code nodeIds} already contain the descendants of the declared nodes. A single
 * question is one unit, a set is one unit per child; every carrying unit counts, also one that fills another
 * minimum quota. The minimum half of an exact rule is an ordinary {@link TopicQuota}.
 *
 * @param id      the rule id, reported when the cap makes a section infeasible
 * @param groupId null bounds the whole section; otherwise only the units of that block (all slots of a slot parent)
 * @param nodeIds nodes (with descendants) a unit carries when any of its MAPIDs is among them
 * @param max     the highest number of carrying units, zero allowed
 */
public record TopicCap(String id, String groupId, List<String> nodeIds, int max) {
    public TopicCap {
        Objects.requireNonNull(id, "id must not be null");
        if (max < 0) throw new IllegalArgumentException("max must not be negative");
        nodeIds = List.copyOf(nodeIds);
    }
}
