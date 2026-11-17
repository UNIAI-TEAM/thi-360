package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Selects one standalone question to remove without splitting a set. */
public final class TrimPolicy {
    private TrimPolicy() {}

    public record Result(SubjectWitness witness, QuestionPick droppedQuestion) {}

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
        List<QuestionPick> preferred = new ArrayList<>();
        for (GroupWitness group : witness.groups()) {
            GroupQuota quota = groups.get(group.groupId());
            for (QuestionPick pick : group.picks()) {
                PathCandidate candidate = selected.get(pick.questionId());
                if (candidate.setId() != null || candidate.units().size() != 1) continue;
                CountingUnit unit = candidate.units().getFirst();
                if (preserveFill && "SHORT_ANSWER".equals(unit.answerType())) continue;
                eligible.add(pick);
                if (!contributesRequiredTopic(quota, unit)) preferred.add(pick);
            }
        }
        List<QuestionPick> choices = preferred.isEmpty() ? eligible : preferred;
        if (choices.isEmpty()) return null;
        QuestionPick dropped = choices.get(new Random(seed).nextInt(choices.size()));
        List<GroupWitness> trimmed = new ArrayList<>();
        for (GroupWitness group : witness.groups()) {
            trimmed.add(new GroupWitness(group.groupId(), group.picks().stream()
                    .filter(pick -> !pick.equals(dropped)).toList()));
        }
        return new Result(new SubjectWitness(witness.subjectCode(), witness.resolvedCount() - 1,
                trimmed), dropped);
    }

    /** UNI-2649 BE-4b extends this check with assigned required topic quotas. */
    private static boolean contributesRequiredTopic(GroupQuota group, CountingUnit unit) {
        return group.mapidGroups().stream().anyMatch(q -> q.quota() > 0
                && unit.mapidIds().stream().anyMatch(q.nodeIds()::contains));
    }
}
