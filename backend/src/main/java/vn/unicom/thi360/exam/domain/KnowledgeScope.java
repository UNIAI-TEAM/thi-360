package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/** MAPID scope. includeDescendants is retained only for rejecting the removed legacy field. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeScope(
        List<UUID> nodeIds,
        KnowledgeMatch match,
        Boolean includeDescendants,
        List<String> competencyCodes) {
    public KnowledgeScope {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        competencyCodes = competencyCodes == null ? List.of() : List.copyOf(competencyCodes);
    }
}
