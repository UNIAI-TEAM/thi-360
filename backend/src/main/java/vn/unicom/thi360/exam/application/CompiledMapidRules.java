package vn.unicom.thi360.exam.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamSection;

/**
 * What {@link MapidRuleCompiler} makes of one section: the section the solver must read (legacy MAPID scope and
 * topic quotas replaced by the rules), the upper bounds and the per-block exclusions that the existing quota
 * primitives cannot state, and the SOURCE conditions.
 *
 * @param effectiveSection the section to build solver input from (its scopes keep no MAPID nodes in MATRIX)
 * @param caps             "at most {@code max} selected units carry a node of {@code nodeIds}", from EXACT counts
 * @param exclusions       block id to the nodes whose questions that block must not draw (ONLY_IN_GROUP)
 * @param subjectSources   one node list per subject-level SOURCE rule: every candidate of every block must match every
 *                         list (a node of the list or below one, A or B or C), and a set only when all its children
 *                         share one node of the list
 * @param groupSources     block id to the lists of the SOURCE rules of that block, ANDed with the subject ones
 */
public record CompiledMapidRules(ExamSection effectiveSection, List<Cap> caps, Map<String, List<UUID>> exclusions,
        List<List<UUID>> subjectSources, Map<String, List<List<UUID>>> groupSources) {
    public CompiledMapidRules {
        caps = List.copyOf(caps);
        exclusions = Map.copyOf(exclusions);
        subjectSources = subjectSources.stream().map(List::copyOf).toList();
        groupSources = Map.copyOf(groupSources);
    }

    public CompiledMapidRules(ExamSection effectiveSection, List<Cap> caps, Map<String, List<UUID>> exclusions) {
        this(effectiveSection, caps, exclusions, List.of(), Map.of());
    }

    /** The SOURCE conditions a candidate of {@code blockId} must all meet: the subject's, then the block's own. */
    public List<List<UUID>> sourcesOf(String blockId) {
        List<List<UUID>> own = groupSources.getOrDefault(blockId, List.of());
        if (own.isEmpty()) {
            return subjectSources;
        }
        List<List<UUID>> all = new java.util.ArrayList<>(subjectSources);
        all.addAll(own);
        return List.copyOf(all);
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
