package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Captures the selected versions and their solver metadata, not mutable current question state. */
final class PreviewSnapshotAssembler {
    static Map<String, PreviewSnapshotStore.PartSnapshot> assemble(PreviewPartIndex index,
            Map<String, GroupWitness> groups,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions,
            List<PreviewSnapshotStore.Dropped> dropped, List<SectionOnPath> sections, CandidateSource source) {
        Map<String, List<GroupWitness>> groupsByPart = new LinkedHashMap<>();
        for (GroupWitness group : groups.values()) {
            String part = index.groupPart(group.groupId());
            if (part == null) throw new IllegalStateException("Selected group has no part");
            groupsByPart.computeIfAbsent(part, ignored -> new ArrayList<>()).add(group);
        }
        Map<String, List<PreviewSnapshotStore.Dropped>> droppedByPart = new LinkedHashMap<>();
        for (var item : dropped) {
            String part = findDroppedPart(index, item, sections, source);
            droppedByPart.computeIfAbsent(part, ignored -> new ArrayList<>()).add(item);
        }
        Map<String, PreviewSnapshotStore.PartSnapshot> result = new LinkedHashMap<>();
        for (var entry : groupsByPart.entrySet()) {
            String part = entry.getKey();
            List<PreviewSnapshotStore.Dropped> lost = droppedByPart.getOrDefault(part, List.of());
            Set<QuestionSourcePort.VersionKey> roots = new LinkedHashSet<>();
            for (GroupWitness group : entry.getValue()) {
                for (QuestionPick pick : group.picks()) roots.add(key(pick));
            }
            for (var item : lost) roots.add(key(item.pick()));
            List<QuestionSourcePort.Candidate> savedCandidates = new ArrayList<>();
            Set<QuestionSourcePort.VersionKey> versionKeys = new LinkedHashSet<>(roots);
            for (var root : roots) {
                var candidate = candidates.get(root);
                if (candidate == null) throw new IllegalStateException("Selected candidate missing");
                savedCandidates.add(candidate);
                if (candidate.setId() != null) {
                    for (var unit : candidate.units()) {
                        if (unit.questionId() != null && !unit.questionId().equals(candidate.questionId())) {
                            versionKeys.add(new QuestionSourcePort.VersionKey(unit.questionId(), root.versionNo()));
                        }
                    }
                }
            }
            List<QuestionSourcePort.Version> savedVersions = new ArrayList<>();
            for (var key : versionKeys) {
                var version = versions.get(key);
                if (version == null) throw new IllegalStateException("Selected version missing");
                savedVersions.add(version);
            }
            result.put(part, new PreviewSnapshotStore.PartSnapshot(List.copyOf(entry.getValue()),
                    List.copyOf(savedCandidates), List.copyOf(savedVersions), List.copyOf(lost)));
        }
        return Map.copyOf(result);
    }

    private static String findDroppedPart(PreviewPartIndex index, PreviewSnapshotStore.Dropped item,
            List<SectionOnPath> sections, CandidateSource source) {
        for (SectionOnPath section : sections) {
            if (!section.subjectCode().equals(item.subjectCode())) continue;
            for (var group : section.groups()) {
                var candidates = source.candidatesFor(group);
                if (candidates != null && candidates.stream().anyMatch(c -> c.questionId().equals(item.pick().questionId()))) {
                    String part = index.groupPart(group.groupId());
                    if (part != null) return part;
                }
            }
        }
        throw new IllegalStateException("Dropped question has no part");
    }

    private static QuestionSourcePort.VersionKey key(QuestionPick pick) {
        return new QuestionSourcePort.VersionKey(UUID.fromString(pick.questionId()), pick.versionNo());
    }
}
