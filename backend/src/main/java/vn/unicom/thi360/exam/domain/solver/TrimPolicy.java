package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Selects one standalone question to remove without splitting a set. */
public final class TrimPolicy {
    private TrimPolicy() {}

    /**
     * {@code witness}/{@code droppedQuestion} are null when {@code blockedBy} says the subject cannot lose a question:
     * its section is a MAPID matrix and every standalone drop would break a topic minimum of a rule (legacy topic
     * quotas never block, spec H3). {@code blockedBy} names the first broken minimum and may be null when the break
     * is only visible to the assignment, not to a plain count.
     */
    public record Result(SubjectWitness witness, QuestionPick droppedQuestion, QuotaRef blockedBy) {
        Result(SubjectWitness witness, QuestionPick droppedQuestion) {
            this(witness, droppedQuestion, null);
        }

        public boolean blocked() {
            return witness == null;
        }
    }

    public static Result trim(SectionOnPath section, SubjectWitness witness,
            CandidateSource source, long seed) {
        Map<String, GroupQuota> groups = new HashMap<>();
        Map<String, PathCandidate> selected = new HashMap<>();
        for (GroupQuota group : section.groups()) {
            groups.put(group.groupId(), group);
            List<PathCandidate> pool = source.candidatesFor(group);
            if (pool != null) {
                for (PathCandidate candidate : pool) {
                    selected.put(candidate.questionId(), candidate);
                }
            }
        }
        int fillCount = 0;
        for (GroupWitness group : witness.groups()) {
            for (QuestionPick pick : group.picks()) {
                PathCandidate candidate = selected.get(pick.questionId());
                if (candidate == null) throw new IllegalStateException("Selected candidate missing");
                for (CountingUnit unit : candidate.units()) {
                    if ("SHORT_ANSWER".equals(unit.answerType())) fillCount++;
                }
            }
        }
        boolean preserveFill = "NATURAL".equals(section.choiceGroup())
                && section.subjectQuota().answerQuotas() != null && fillCount == 1;
        List<QuestionPick> eligible = new ArrayList<>();
        List<QuestionPick> keeping = new ArrayList<>();
        List<QuestionPick> preferred = new ArrayList<>();
        Map<QuestionPick, Map<TopicAssignments.Pick, List<TopicAssignment>>> reassigned = new HashMap<>();
        for (GroupWitness group : witness.groups()) {
            GroupQuota quota = groups.get(group.groupId());
            for (QuestionPick pick : group.picks()) {
                PathCandidate candidate = selected.get(pick.questionId());
                if (candidate.setId() != null || candidate.units().size() != 1) continue;
                CountingUnit unit = candidate.units().getFirst();
                if (preserveFill && "SHORT_ANSWER".equals(unit.answerType())) continue;
                eligible.add(pick);
                Map<TopicAssignments.Pick, List<TopicAssignment>> assignment =
                        TopicAssignments.reassign(section, witness, selected, pick);
                if (assignment == null) continue; // the minimums of the subject would no longer hold
                keeping.add(pick);
                reassigned.put(pick, assignment);
                if (!contributesRequiredTopic(quota, unit)) preferred.add(pick);
            }
        }
        if (eligible.isEmpty()) return null;
        // The topic minimums of a MAPID matrix are hard (UNI-2758 R1-1); a legacy topic quota keeps the H3 fallback of
        // base: when no other drop exists, one of its contributors may go (review R2-1).
        boolean strict = section.subjectQuota().mapidMatrix();
        List<QuestionPick> fallback = strict ? keeping : eligible;
        if (fallback.isEmpty()) {
            return new Result(null, null, brokenMinimum(section, witness, selected, eligible.getFirst()));
        }
        List<QuestionPick> choices = preferred.isEmpty() ? fallback : preferred;
        QuestionPick dropped = choices.get(new Random(seed).nextInt(choices.size()));
        List<GroupWitness> trimmed = new ArrayList<>();
        Map<TopicAssignments.Pick, List<TopicAssignment>> assignment = reassigned.get(dropped);
        for (int g = 0; g < witness.groups().size(); g++) {
            GroupWitness group = witness.groups().get(g);
            List<QuestionPick> picks = new ArrayList<>();
            for (int i = 0; i < group.picks().size(); i++) {
                QuestionPick pick = group.picks().get(i);
                if (pick.equals(dropped)) continue;
                picks.add(assignment == null ? pick : new QuestionPick(pick.questionId(), pick.versionNo(),
                        assignment.getOrDefault(new TopicAssignments.Pick(g, i), List.of())));
            }
            trimmed.add(new GroupWitness(group.groupId(), picks));
        }
        return new Result(new SubjectWitness(witness.subjectCode(), witness.resolvedCount() - 1,
                trimmed), dropped);
    }

    /** Existing MAPID minimum quotas also influence the preferred drop. */
    private static boolean contributesRequiredTopic(GroupQuota group, CountingUnit unit) {
        return group.mapidGroups().stream().anyMatch(q -> q.quota() > 0
                && unit.mapidIds().stream().anyMatch(q.nodeIds()::contains));
    }

    /** The first topic minimum (a MAPID rule among them) that the paper loses when {@code dropped} goes, or null. */
    private static QuotaRef brokenMinimum(SectionOnPath section, SubjectWitness witness,
            Map<String, PathCandidate> selected, QuestionPick dropped) {
        Map<String, GroupQuota> groups = new HashMap<>();
        for (GroupQuota group : section.groups()) groups.put(group.groupId(), group);
        for (TopicQuota quota : section.subjectQuota().topicQuotas()) {
            if (remaining(witness, groups, selected, dropped, quota, null) < quota.count()) {
                return new QuotaRef(section.subjectCode(), section.sectionId(), "TOPIC", quota.id());
            }
        }
        for (GroupQuota group : section.groups()) {
            String scope = TopicAssignments.scopeId(group);
            List<TopicQuota> quotas = group.slotParent() == null ? group.topicQuotas()
                    : group.slotParent().topicQuotas();
            for (TopicQuota quota : quotas) {
                if (remaining(witness, groups, selected, dropped, quota, scope) < quota.count()) {
                    return new QuotaRef(section.subjectCode(), section.sectionId(), "TOPIC", quota.id());
                }
            }
        }
        return null;
    }

    /** Units still matching {@code quota} without {@code dropped}, in one topic scope or in the whole section. */
    private static int remaining(SubjectWitness witness, Map<String, GroupQuota> groups,
            Map<String, PathCandidate> selected, QuestionPick dropped, TopicQuota quota, String scopeId) {
        int count = 0;
        for (GroupWitness groupWitness : witness.groups()) {
            String scope = TopicAssignments.scopeId(groups.get(groupWitness.groupId()));
            if (scopeId != null && !scopeId.equals(scope)) continue;
            for (QuestionPick pick : groupWitness.picks()) {
                if (pick.equals(dropped)) continue;
                for (CountingUnit unit : selected.get(pick.questionId()).units()) {
                    if (quota.matches(scope, unit)) count++;
                }
            }
        }
        return count;
    }
}
