package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.TopicQuota;

/**
 * Static conflicts between the MAPID rules of one section (UNI-1704 v2, "xung đột"). Works on what
 * {@link MapidRuleCompiler} makes of the rules (minimums and caps with percentages already resolved), never on the
 * declared legacy fields. A rule counts a question that carries its node or any descendant (D23), so rule A
 * "covers" rule B when every node of B is a node of A or below one.
 */
final class MapidRuleConflicts {
    static final String GROUP_MINIMUM_ABOVE_GROUP_UNITS = "GROUP_MINIMUM_ABOVE_GROUP_UNITS";
    static final String SUBJECT_EXACT_BELOW_GROUP_MINIMUM = "SUBJECT_EXACT_BELOW_GROUP_MINIMUM";
    static final String GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT = "GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT";
    static final String EXACT_COUNTS_DIFFER = "EXACT_COUNTS_DIFFER";
    static final String EXACT_BELOW_MINIMUM = "EXACT_BELOW_MINIMUM";
    static final String ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP_RULE = "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP_RULE";
    static final String GROUP_RULE_OUTSIDE_SUBJECT_SOURCE = "GROUP_RULE_OUTSIDE_SUBJECT_SOURCE";
    static final String SUBJECT_MINIMUM_ABOVE_ONLY_GROUP_UNITS = "SUBJECT_MINIMUM_ABOVE_ONLY_GROUP_UNITS";

    /**
     * @param ruleIds  every rule that cannot hold together, in declaration order
     * @param blockId  the group the conflict is located at, null for a subject-level conflict
     * @param required the number the rule asks for, null when the kind has none
     * @param available the number the group or the exact rule allows, null when the kind has none
     */
    record Conflict(String kind, List<String> ruleIds, String blockId, Integer required, Integer available) { }

    private record Resolved(MapidRule rule, int order, int minimum, Integer exact) {
        String groupId() { return rule.groupId(); }
        Set<UUID> nodes() { return new LinkedHashSet<>(rule.nodeIds()); }
    }

    private final Map<UUID, UUID> parents;

    private MapidRuleConflicts(Map<UUID, UUID> parents) {
        this.parents = parents;
    }

    /** Topic parents map each knowledge node to its direct parent; roots are absent. */
    static List<Conflict> find(ExamSection section, Map<UUID, UUID> parents) {
        if (section.mapidMode() != MapidMode.MATRIX || section.mapidRules().isEmpty()) return List.of();
        CompiledMapidRules compiled;
        try {
            compiled = MapidRuleCompiler.compile(section);
        } catch (IllegalArgumentException malformed) {
            return List.of(); // the structure validator reports a malformed rule; there is nothing to compare
        }
        return new MapidRuleConflicts(parents).conflicts(section, compiled);
    }

    private List<Conflict> conflicts(ExamSection section, CompiledMapidRules compiled) {
        Map<String, Integer> minimums = new LinkedHashMap<>();
        for (TopicQuota quota : compiled.effectiveSection().topicQuotas()) minimums.put(quota.id(), quota.count());
        for (QuestionBlock block : compiled.effectiveSection().blocks()) {
            for (TopicQuota quota : block.topicQuotas()) minimums.put(quota.id(), quota.count());
        }
        Map<String, Integer> exacts = new LinkedHashMap<>();
        compiled.caps().forEach(cap -> exacts.put(cap.ruleId(), cap.max()));
        List<Resolved> rules = new ArrayList<>();
        List<Resolved> sources = new ArrayList<>();
        int order = 0;
        for (MapidRule rule : section.mapidRules()) {
            order++;
            if (rule.nodeIds().isEmpty()) continue;
            // A rule of a gone group is reported as MAPID_RULE_GROUP_MISSING and compiles to nothing: not compared.
            if (rule.groupId() != null && section.blocks().stream().noneMatch(block -> rule.groupId().equals(block.id()))) continue;
            if (rule.type() == MapidRuleType.SOURCE) {
                sources.add(new Resolved(rule, order, 0, null));
                continue;
            }
            int minimum = minimums.getOrDefault(rule.id(), 0);
            rules.add(new Resolved(rule, order, minimum, exacts.get(rule.id())));
        }
        Map<String, Conflict> found = new LinkedHashMap<>();
        for (Resolved rule : rules) {
            Integer units = rule.groupId() == null ? null : units(section, rule.groupId());
            if (units != null && rule.minimum() > units) {
                add(found, new Conflict(GROUP_MINIMUM_ABOVE_GROUP_UNITS, List.of(rule.rule().id()), rule.groupId(),
                        rule.minimum(), units));
            }
        }
        for (Resolved exact : rules) {
            if (exact.exact() == null) continue;
            for (Resolved other : rules) {
                if (other == exact || other.minimum() <= exact.exact() || !covers(exact, other)) continue;
                if (java.util.Objects.equals(other.groupId(), exact.groupId())) {
                    add(found, new Conflict(other.exact() != null ? EXACT_COUNTS_DIFFER : EXACT_BELOW_MINIMUM,
                            ordered(exact, other), exact.groupId(), other.minimum(), exact.exact()));
                } else if (exact.groupId() == null) {
                    add(found, new Conflict(SUBJECT_EXACT_BELOW_GROUP_MINIMUM, ordered(exact, other), other.groupId(),
                            other.minimum(), exact.exact()));
                }
            }
            if (exact.groupId() == null) subjectSum(found, exact, rules);
        }
        for (Resolved only : rules) {
            if (only.rule().type() != MapidRuleType.ONLY_IN_GROUP) continue;
            for (Resolved other : rules) {
                if (other.groupId() == null || other.groupId().equals(only.groupId()) || other.minimum() <= 0
                        || !covers(only, other)) continue;
                add(found, new Conflict(ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP_RULE, ordered(only, other),
                        other.groupId(), other.minimum(), 0));
            }
            // Decision 2026-11-26 point 5: the whole subject cannot ask for more of the node than group A holds.
            Integer units = units(section, only.groupId());
            if (units == null) continue;
            for (Resolved subject : rules) {
                if (subject.groupId() != null || subject.minimum() <= units || !covers(only, subject)) continue;
                add(found, new Conflict(SUBJECT_MINIMUM_ABOVE_ONLY_GROUP_UNITS, ordered(only, subject),
                        only.groupId(), subject.minimum(), units));
            }
        }
        outsideSubjectSource(found, rules, sources);
        return List.copyOf(found.values());
    }

    /**
     * Decision 2026-11-26 point 4: the whole-subject source is the outer boundary, so every node of a group rule
     * (a group SOURCE too) has to be a node of the subject sources or below one.
     */
    private void outsideSubjectSource(Map<String, Conflict> found, List<Resolved> rules, List<Resolved> sources) {
        List<Resolved> subjectSources = sources.stream().filter(source -> source.groupId() == null).toList();
        if (subjectSources.isEmpty()) return;
        Set<UUID> boundary = new LinkedHashSet<>();
        subjectSources.forEach(source -> boundary.addAll(source.nodes()));
        List<Resolved> groupRules = new ArrayList<>(rules);
        groupRules.addAll(sources);
        groupRules.sort(Comparator.comparingInt(Resolved::order));
        for (Resolved rule : groupRules) {
            if (rule.groupId() == null || within(boundary, rule.nodes())) continue;
            List<Resolved> involved = new ArrayList<>(subjectSources);
            involved.add(rule);
            add(found, new Conflict(GROUP_RULE_OUTSIDE_SUBJECT_SOURCE, ordered(involved.toArray(Resolved[]::new)),
                    rule.groupId(), null, null));
        }
    }

    /** The minimums of several groups add up: a question fills one slot, so each group needs its own carriers. */
    private void subjectSum(Map<String, Conflict> found, Resolved exact, List<Resolved> rules) {
        List<Resolved> covered = new ArrayList<>();
        int total = 0;
        for (Resolved other : rules) {
            if (other.groupId() == null || other.minimum() <= 0 || !covers(exact, other)) continue;
            covered.add(other);
            total += other.minimum();
        }
        if (covered.size() < 2 || total <= exact.exact()) return;
        List<Resolved> all = new ArrayList<>();
        all.add(exact);
        all.addAll(covered);
        add(found, new Conflict(GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT, ordered(all.toArray(Resolved[]::new)), null,
                total, exact.exact()));
    }

    private static List<String> ordered(Resolved... rules) {
        return java.util.Arrays.stream(rules).sorted(java.util.Comparator.comparingInt(Resolved::order))
                .map(rule -> rule.rule().id()).toList();
    }

    private static void add(Map<String, Conflict> found, Conflict conflict) {
        found.putIfAbsent(conflict.kind() + "|" + String.join(",", conflict.ruleIds()), conflict);
    }

    /** {@code wide} counts every question {@code narrow} counts: each node of narrow is a node of wide or below one. */
    private boolean covers(Resolved wide, Resolved narrow) {
        return within(wide.nodes(), narrow.nodes());
    }

    /** Every node of {@code narrowNodes} is one of {@code wideNodes} or below one; false when there is none. */
    private boolean within(Set<UUID> wideNodes, Set<UUID> narrowNodes) {
        if (narrowNodes.isEmpty()) return false;
        for (UUID node : narrowNodes) {
            boolean covered = false;
            for (UUID candidate : wideNodes) {
                if (candidate.equals(node) || isAncestor(candidate, node)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return true;
    }

    private boolean isAncestor(UUID ancestor, UUID node) {
        Set<UUID> visited = new LinkedHashSet<>();
        UUID current = parents.get(node);
        while (current != null && visited.add(current)) {
            if (current.equals(ancestor)) return true;
            current = parents.get(current);
        }
        return false;
    }

    /** Units the group draws (a set counts its children); null when the size cannot be known. */
    private static Integer units(ExamSection section, String groupId) {
        QuestionBlock target = null;
        int fixed = 0;
        for (QuestionBlock block : section.blocks()) {
            if (Boolean.TRUE.equals(block.omitted())) continue;
            if (block.id().equals(groupId)) target = block;
            fixed += fixedUnits(block);
        }
        if (target == null) return null;
        if (target.setCount() != null && target.setSize() != null || target.questionCount() != null) {
            return fixedUnits(target);
        }
        if (section.questionCount() == null) return null;
        // A "take the remainder" group: what the other groups leave of the section.
        return Math.max(0, section.questionCount() - fixed);
    }

    private static int fixedUnits(QuestionBlock block) {
        if (block.setCount() != null && block.setSize() != null) return Math.multiplyExact(block.setCount(), block.setSize());
        return block.questionCount() == null ? 0 : block.questionCount();
    }
}
