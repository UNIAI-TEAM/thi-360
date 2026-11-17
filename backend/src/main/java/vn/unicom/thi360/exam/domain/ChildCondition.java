package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChildCondition(Integer from, Integer to, List<UUID> nodeIds) {
    public ChildCondition {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
    }
}
