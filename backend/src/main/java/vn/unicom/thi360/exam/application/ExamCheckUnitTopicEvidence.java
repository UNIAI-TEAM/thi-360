package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.TopicQuota;

/** An upper bound from complete source pools can prove an individual topic quota impossible. */
final class ExamCheckUnitTopicEvidence {
    private ExamCheckUnitTopicEvidence() { }

    static List<ObjectNode> issues(PathResult result, ExamStructureDocument document,
            SolverInputBuilder.SolverCandidates candidates, ExamCheckRules rules,
            Clock clock, Instant deadline, int maxCandidates) {
        Map<String, SectionOnPath> sections = new LinkedHashMap<>();
        result.path().sections().forEach(section -> sections.putIfAbsent(section.sectionId(), section));
        result.path().picks().forEach(tuple -> tuple.forEach(pick -> pick.resolvedSections()
                .forEach(section -> sections.putIfAbsent(section.sectionId(), section))));
        List<ExamCheckRules.TopicQuotaEvidence> evidence = new ArrayList<>();
        for (var section : sections.values()) {
            String partId = partId(document, section.sectionId());
            if (partId == null) continue;
            for (var quota : section.subjectQuota().topicQuotas()) {
                add(evidence, section, partId, null, quota, section.groups(), candidates, clock, deadline, maxCandidates);
            }
            Map<String, List<GroupQuota>> groups = new LinkedHashMap<>();
            for (var group : section.groups()) {
                String id = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
                groups.computeIfAbsent(id, ignored -> new ArrayList<>()).add(group);
            }
            for (var entry : groups.entrySet()) {
                var first = entry.getValue().getFirst();
                var quotas = first.slotParent() == null ? first.topicQuotas() : first.slotParent().topicQuotas();
                for (var quota : quotas) {
                    add(evidence, section, partId, entry.getKey(), quota, entry.getValue(), candidates, clock, deadline, maxCandidates);
                }
            }
        }
        return rules.topicQuotaIssues(evidence);
    }

    private static void add(List<ExamCheckRules.TopicQuotaEvidence> evidence, SectionOnPath section,
            String partId, String blockId, TopicQuota quota, List<GroupQuota> groups,
            SolverInputBuilder.SolverCandidates candidates, Clock clock, Instant deadline, int maxCandidates) {
        long upperBound = 0;
        for (var group : groups) {
            String groupId = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
            if (!quota.groupIds().isEmpty() && !quota.groupIds().contains(groupId)) continue;
            if (!clock.instant().isBefore(deadline)) return;
            var pool = candidates.candidatesFor(group);
            if (pool.size() > maxCandidates || !clock.instant().isBefore(deadline)) return;
            for (var candidate : pool) for (var unit : candidate.units()) if (matches(quota, unit)) upperBound++;
        }
        if (upperBound < quota.count()) {
            var location = new ExamCheckRules.EvidenceLocation(partId, section.sectionId(), section.subjectCode(),
                    blockId, null, null, null, List.of());
            evidence.add(new ExamCheckRules.TopicQuotaEvidence(location, quota.id(), quota.count(), upperBound));
        }
    }

    private static boolean matches(TopicQuota quota, CountingUnit unit) {
        return (quota.gradeCodes().isEmpty() || quota.gradeCodes().contains(unit.gradeCode()))
                && (quota.difficultyCodes().isEmpty() || quota.difficultyCodes().contains(unit.difficultyCode()))
                && unit.mapidIds().stream().anyMatch(quota.nodeIds()::contains);
    }

    private static String partId(ExamStructureDocument document, String sectionId) {
        for (ExamPart part : document.parts()) {
            if (part.sections().stream().anyMatch(section -> sectionId.equals(section.id()))
                    || part.subjectPool().stream().anyMatch(section -> sectionId.equals(section.id()))
                    || part.options().stream().flatMap(option -> option.sections().stream())
                            .anyMatch(section -> sectionId.equals(section.id()))) return part.id();
        }
        return null;
    }
}
