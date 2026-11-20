package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One MAPID rule of a section (UNI-1704 v2). {@code groupId} null targets the whole section, otherwise a block
 * id of that section. {@code nodeIds} have OR semantics inside the rule. {@code count} / {@code percent} are
 * only meaningful for {@link MapidRuleType#COUNT} and {@link MapidRuleType#ONLY_IN_GROUP}; when both are set the
 * percent wins, as for topic quotas. The null elements of {@code nodeIds} are kept so the validator can report them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapidRule(
        String id,
        MapidRuleType type,
        String groupId,
        List<UUID> nodeIds,
        MapidComparator comparator,
        Integer count,
        BigDecimal percent) {
    public MapidRule {
        nodeIds = nodeIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(nodeIds));
    }
}
