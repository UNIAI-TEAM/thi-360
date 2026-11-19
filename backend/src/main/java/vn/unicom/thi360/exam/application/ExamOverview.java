package vn.unicom.thi360.exam.application;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamOverviewRequest;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Applies partial overview changes without replacing blocks or quotas. */
final class ExamOverview {
    private ExamOverview() { }

    record Change(ExamOverviewRequest.Row row, boolean countChanged, boolean durationChanged,
                  boolean pointsChanged, Integer provenanceOrder) { }

    static ExamStructureDocument apply(ExamStructureDocument document, List<ExamOverviewRequest.Row> rows) {
        return applyChanges(document, rows.stream().map(row -> row == null ? null
                : new Change(row, row.questionCount() != null, row.durationMinutes() != null,
                        row.maxPoints() != null, null)).toList(), true);
    }

    static ExamStructureDocument applyChanges(ExamStructureDocument document, List<Change> changes) {
        return applyChanges(document, changes, false);
    }

    private static ExamStructureDocument applyChanges(ExamStructureDocument document, List<Change> changes,
                                                       boolean propagateCount) {
        Map<String, Change> parts = new HashMap<>();
        Map<String, Change> sections = new HashMap<>();
        for (var change : changes) {
            var row = change == null ? null : change.row();
            if (row == null || row.partId() == null || row.partId().isBlank()) {
                throw invalid();
            }
            Map<String, Change> target = row.sectionId() == null ? parts : sections;
            String key = row.sectionId() == null ? row.partId() : row.partId() + "\u0000" + row.sectionId();
            if (target.putIfAbsent(key, change) != null || row.questionCount() != null && row.questionCount() < 0
                    || row.durationMinutes() != null && row.durationMinutes() < 0
                    || row.maxPoints() != null && row.maxPoints().signum() <= 0) {
                throw invalid();
            }
        }
        Set<String> matched = new HashSet<>();
        var result = document.parts().stream().map(part -> {
            var change = parts.get(part.id());
            var row = change == null ? null : change.row();
            if (row != null) matched.add(part.id());
            var fixed = part.sections().stream().map(s -> updateSection(part.id(), s, sections, matched)).toList();
            var pool = part.subjectPool().stream().map(s -> updateSection(part.id(), s, sections, matched)).toList();
            var options = part.options().stream().map(o -> {
                var updated = o.sections().stream().map(s -> updateSection(part.id(), s, sections, matched)).toList();
                return new ExamPartOption(o.id(), o.kind(), o.code(), o.name(), o.chooseSubjects(), o.countsByPick(),
                        updated, o.blocks(), propagateCount && countsChanged(o.sections(), updated)
                                && Boolean.TRUE.equals(o.expertConfirmed()) ? Boolean.FALSE : o.expertConfirmed());
            }).toList();
            Integer count = change != null && change.countChanged() ? row.questionCount() : part.questionCount();
            if (propagateCount && change != null && change.countChanged() && fixed.size() == 1
                    && part.subjectPool().isEmpty() && part.options().isEmpty()
                    && !sections.containsKey(part.id() + "\u0000" + fixed.getFirst().id())) {
                ExamSection s = fixed.getFirst();
                fixed = List.of(new ExamSection(s.id(), s.subjectCode(), count, s.knowledgeScope(), s.answerQuotas(),
                        s.matrix(), s.choiceGroup(), s.blocks(), s.topicQuotas(), s.audioRequirement(),
                        s.mapidMode(), s.mapidRules()));
            }
            return new ExamPart(part.id(), part.order(), part.name(),
                    change != null && change.durationChanged() ? row.durationMinutes() : part.durationMinutes(),
                    count, onPaper(part, change != null && change.countChanged(), count), part.questionCountRequired(),
                    part.participation(),
                    change != null && change.pointsChanged() ? row.maxPoints() : part.maxPoints(), fixed, options,
                    pool, part.codeSource(), part.displaySegments());
        }).toList();
        if (matched.size() != parts.size() + sections.size()) throw invalid();
        return new ExamStructureDocument(result, document.examQuotas());
    }

    /**
     * The printed count of a FIXED (ALL) part follows an overview count that grows past it; a smaller count and
     * every non-FIXED part (CHOICE, or coded with a codeSource) keep the stored value, as before.
     */
    private static Integer onPaper(ExamPart part, boolean countChanged, Integer count) {
        Integer onPaper = part.questionCountOnPaper();
        // A coded (QDA/BCA) part is stored as ALL but its catalog choice paths decide the printed count.
        boolean fixed = part.codeSource() == null
                && (part.participation() == null || part.participation().mode() != ParticipationMode.CHOICE);
        return countChanged && fixed && onPaper != null && count != null && onPaper < count ? count : onPaper;
    }

    static List<ExamOverviewRequest.Row> rows(ExamStructureDocument document) {
        List<ExamOverviewRequest.Row> result = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            result.add(new ExamOverviewRequest.Row(part.id(), null, null, null,
                    part.questionCount(), part.durationMinutes(), part.maxPoints()));
            List<ExamSection> sections = new ArrayList<>(part.sections());
            sections.addAll(part.subjectPool());
            part.options().forEach(option -> sections.addAll(option.sections()));
            sections.forEach(section -> result.add(new ExamOverviewRequest.Row(part.id(), section.id(), null,
                    section.subjectCode(), section.questionCount(), null, null)));
        }
        return result;
    }

    static List<Change> changes(ExamStructureDocument current, ExamStructureDocument baseline) {
        List<Change> changes = new ArrayList<>();
        for (ExamPart part : current.parts()) {
            List<ExamPart> matches = baseline.parts().stream()
                    .filter(old -> part.id() != null && part.id().equals(old.id())).toList();
            if (matches.isEmpty() && part.order() != null) {
                matches = baseline.parts().stream()
                        .filter(old -> part.order().equals(old.order())).toList();
            }
            ExamPart before = matches.size() == 1 ? matches.getFirst() : null;
            if (before == null || before.order() == null) {
                changes.add(new Change(new ExamOverviewRequest.Row(part.id(), null, null, null,
                        part.questionCount(), part.durationMinutes(), part.maxPoints()), true, true, true, null));
                continue;
            }
            boolean count = !java.util.Objects.equals(part.questionCount(), before.questionCount());
            boolean duration = !java.util.Objects.equals(part.durationMinutes(), before.durationMinutes());
            boolean points = part.maxPoints() == null || before.maxPoints() == null
                    ? !java.util.Objects.equals(part.maxPoints(), before.maxPoints())
                    : part.maxPoints().compareTo(before.maxPoints()) != 0;
            if (count || duration || points) {
                changes.add(new Change(new ExamOverviewRequest.Row(part.id(), null, null, null,
                        part.questionCount(), part.durationMinutes(), part.maxPoints()), count, duration, points,
                        before.order()));
            }
            List<ExamSection> sections = new ArrayList<>(before.sections());
            sections.addAll(before.subjectPool());
            before.options().forEach(o -> sections.addAll(o.sections()));
            List<ExamSection> actual = new ArrayList<>(part.sections());
            actual.addAll(part.subjectPool());
            part.options().forEach(o -> actual.addAll(o.sections()));
            for (ExamSection section : actual) {
                List<ExamSection> matching = sections.stream()
                        .filter(old -> section.id() != null && section.id().equals(old.id())).toList();
                if (matching.isEmpty() && section.subjectCode() != null) {
                    matching = sections.stream()
                            .filter(old -> section.subjectCode().equals(old.subjectCode())).toList();
                }
                ExamSection old = matching.size() == 1 ? matching.getFirst() : null;
                if (old == null || !java.util.Objects.equals(section.questionCount(), old.questionCount())) {
                    changes.add(new Change(new ExamOverviewRequest.Row(part.id(), section.id(), null,
                            old == null ? section.subjectCode() : old.subjectCode(), section.questionCount(), null, null),
                            true, false, false, before.order()));
                }
            }
        }
        return changes;
    }

    static List<Change> presentIn(ExamStructureDocument destination, List<Change> changes) {
        List<Change> result = new ArrayList<>();
        for (var change : changes) {
            var row = change.row();
            if (change.provenanceOrder() == null) throw invalid();
            List<ExamPart> matches = destination.parts().stream()
                    .filter(part -> java.util.Objects.equals(part.order(), change.provenanceOrder()))
                    .toList();
            if (matches.size() != 1) continue;
            ExamPart part = matches.getFirst();
            String sectionId = null;
            if (row.sectionId() != null) {
                List<ExamSection> candidates = new ArrayList<>(part.sections());
                candidates.addAll(part.subjectPool());
                part.options().forEach(option -> candidates.addAll(option.sections()));
                List<ExamSection> matching = candidates.stream()
                        .filter(section -> row.subjectCode() != null
                                && java.util.Objects.equals(section.subjectCode(), row.subjectCode()))
                        .toList();
                if (matching.size() != 1) continue;
                sectionId = matching.getFirst().id();
            }
            result.add(new Change(new ExamOverviewRequest.Row(part.id(), sectionId, null, row.subjectCode(),
                    row.questionCount(), row.durationMinutes(), row.maxPoints()),
                    change.countChanged(), change.durationChanged(), change.pointsChanged(), change.provenanceOrder()));
        }
        return result;
    }

    private static ExamSection updateSection(String partId, ExamSection section,
            Map<String, Change> rows, Set<String> matched) {
        String key = partId + "\u0000" + section.id();
        var change = rows.get(key);
        if (change == null) return section;
        var row = change.row();
        if (change.durationChanged() || change.pointsChanged()) throw invalid();
        matched.add(key);
        return new ExamSection(section.id(), section.subjectCode(),
                change.countChanged() ? row.questionCount() : section.questionCount(),
                section.knowledgeScope(), section.answerQuotas(), section.matrix(), section.choiceGroup(),
                section.blocks(), section.topicQuotas(), section.audioRequirement(), section.mapidMode(),
                section.mapidRules());
    }

    /** A changed section count means the expert never saw the option as it now is. */
    private static boolean countsChanged(List<ExamSection> before, List<ExamSection> after) {
        for (int i = 0; i < before.size(); i++) {
            if (!java.util.Objects.equals(before.get(i).questionCount(), after.get(i).questionCount())) return true;
        }
        return false;
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
    }
}
