package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MAPID scope. Legacy match/includeDescendants are captured only for 422 validation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KnowledgeScope {
    private final List<UUID> nodeIds;
    private final List<String> competencyCodes;
    private final Map<String, JsonNode> legacyFields = new LinkedHashMap<>();

    @JsonCreator
    public KnowledgeScope(@JsonProperty("nodeIds") List<UUID> nodeIds,
            @JsonProperty("competencyCodes") List<String> competencyCodes) {
        this.nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        this.competencyCodes = competencyCodes == null ? List.of() : List.copyOf(competencyCodes);
    }

    @JsonProperty("nodeIds")
    public List<UUID> nodeIds() {
        return nodeIds;
    }

    @JsonProperty("competencyCodes")
    public List<String> competencyCodes() {
        return competencyCodes;
    }

    @JsonAnySetter
    public void captureLegacyField(String name, JsonNode value) {
        if ("match".equals(name) || "includeDescendants".equals(name)) {
            legacyFields.put(name, value);
        }
    }

    @JsonIgnore
    public boolean hasLegacyFields() {
        return !legacyFields.isEmpty();
    }

    @JsonIgnore
    public Map<String, JsonNode> legacyFields() {
        return Collections.unmodifiableMap(legacyFields);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KnowledgeScope that)) {
            return false;
        }
        return nodeIds.equals(that.nodeIds)
                && competencyCodes.equals(that.competencyCodes)
                && legacyFields.equals(that.legacyFields);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(nodeIds, competencyCodes, legacyFields);
    }
}
