package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.api.ValidationException;

/** Splits a resolved path into retained snapshots and the parts that must be solved again. */
final class PreviewRegeneration {
    record Selection(SolvePath path, Map<String, GroupWitness> groups,
                     Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
                     Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions,
                     List<PreviewSnapshotStore.Dropped> dropped, Set<String> excluded,
                     Map<String, PreviewSnapshotStore.PartSnapshot> retained) {}

    static Selection prepare(ExamStructureDocument document, PreviewPartIndex index, SolvePath path,
            PreviewSnapshotStore.Snapshot base, List<String> regeneratePartIds) {
        if (regeneratePartIds == null || regeneratePartIds.isEmpty()) throw new ValidationException((String) null);
        Set<String> regenerate = new HashSet<>(regeneratePartIds);
        Set<String> partIds = new HashSet<>();
        document.parts().forEach(part -> partIds.add(part.id()));
        if (regenerate.size() != regeneratePartIds.size() || !partIds.containsAll(regenerate)) {
            throw new ValidationException((String) null);
        }
        Map<String, Set<String>> expected = new HashMap<>();
        for (SectionOnPath section : path.sections()) expected(index, section, expected);
        for (List<SubjectPick> choices : path.picks()) {
            for (SubjectPick pick : choices) for (SectionOnPath section : pick.resolvedSections()) expected(index, section, expected);
        }
        Map<String, GroupWitness> groups = new HashMap<>();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates = new HashMap<>();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions = new HashMap<>();
        List<PreviewSnapshotStore.Dropped> dropped = new ArrayList<>();
        Set<String> excluded = new HashSet<>();
        Map<String, PreviewSnapshotStore.PartSnapshot> retained = new HashMap<>();
        for (var entry : expected.entrySet()) {
            if (regenerate.contains(entry.getKey())) continue;
            var previous = base.parts().get(entry.getKey());
            if (previous == null) throw new ValidationException((String) null);
            retained.put(entry.getKey(), previous);
            Set<String> actual = new HashSet<>();
            for (GroupWitness group : previous.groups()) {
                actual.add(group.groupId());
                groups.put(group.groupId(), group);
                for (QuestionPick pick : group.picks()) excluded.add(pick.questionId());
            }
            if (!actual.equals(entry.getValue())) throw new ValidationException((String) null);
            for (var candidate : previous.candidates()) {
                candidates.put(new QuestionSourcePort.VersionKey(candidate.questionId(), candidate.versionNo()), candidate);
                if (excluded.contains(candidate.questionId().toString())) {
                    candidate.units().stream().map(QuestionSourcePort.Unit::questionId)
                            .filter(id -> id != null).map(UUID::toString).forEach(excluded::add);
                }
            }
            for (var version : previous.versions()) versions.put(version.key(), version);
            dropped.addAll(previous.droppedQuestions());
        }
        List<SectionOnPath> sections = path.sections().stream()
                .filter(section -> regenerate.contains(index.sectionPart(section.sectionId()))).toList();
        Map<String, String> optionParts = new HashMap<>();
        path.chosen().forEach(option -> optionParts.put(option.optionId(), option.partId()));
        List<SubjectChoice> choices = new ArrayList<>();
        List<List<SubjectPick>> picks = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (regenerate.contains(optionParts.get(choice.optionId()))) {
                choices.add(choice);
                picks.add(path.picks().get(i));
            }
        }
        return new Selection(new SolvePath(path.pathIndex(), path.chosen(), sections, choices, picks),
                groups, candidates, versions, dropped, excluded, retained);
    }

    private static void expected(PreviewPartIndex index, SectionOnPath section, Map<String, Set<String>> result) {
        String part = index.sectionPart(section.sectionId());
        if (part == null) throw new IllegalStateException("Selected section has no part");
        Set<String> groups = result.computeIfAbsent(part, ignored -> new HashSet<>());
        section.groups().forEach(group -> groups.add(group.groupId()));
    }
}
