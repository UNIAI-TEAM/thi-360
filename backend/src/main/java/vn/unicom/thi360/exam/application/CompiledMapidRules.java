package vn.unicom.thi360.exam.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamSection;

/**
 * What {@link MapidRuleCompiler} makes of one section: the section the solver must read (legacy MAPID scope and
 * topic quotas replaced by the rules), the upper bounds and the per-block exclusions that the existing quota
 * primitives cannot state.
 *
 * @param effectiveSection the section to build solver input from
 * @param caps             "at most {@code max} selected units carry a node of {@code nodeIds}", from EXACT counts
 * @param exclusions       block id to the nodes whose questions that block must not draw (ONLY_IN_GROUP)
 */
public record CompiledMapidRules(ExamSection effectiveSection, List<Cap> caps, Map<String, List<UUID>> exclusions) {
    public CompiledMapidRules {
        caps = List.copyOf(caps);
        exclusions = Map.copyOf(exclusions);
    }

    /** An upper bound over a whole section ({@code SUBJECT}) or over one block ({@code GROUP}, then {@code groupId} is set). */
    public record Cap(String ruleId, Scope scope, String groupId, List<UUID> nodeIds, int max) {
        public Cap {
            nodeIds = List.copyOf(nodeIds);
        }

        public enum Scope {
            SUBJECT,
            GROUP
        }
    }
}
