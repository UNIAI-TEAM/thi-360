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
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Applies partial overview changes without replacing blocks or quotas. */
final class ExamOverview {
    private ExamOverview() { }

    static ExamStructureDocument apply(ExamStructureDocument document, List<ExamOverviewRequest.Row> rows) {
        Map<String, ExamOverviewRequest.Row> parts = new HashMap<>();
        Map<String, ExamOverviewRequest.Row> sections = new HashMap<>();
        for (var row : rows) {
            if (row == null || row.partId() == null || row.partId().isBlank()) {
                throw invalid();
            }
            Map<String, ExamOverviewRequest.Row> target = row.sectionId() == null ? parts : sections;
            String key = row.sectionId() == null ? row.partId() : row.partId() + "\u0000" + row.sectionId();
            if (target.putIfAbsent(key, row) != null || row.questionCount() != null && row.questionCount() < 0
                    || row.durationMinutes() != null && row.durationMinutes() < 0
                    || row.maxPoints() != null && row.maxPoints().signum() <= 0) {
                throw invalid();
            }
        }
        Set<String> matched = new HashSet<>();
        var result = document.parts().stream().map(part -> {
            var row = parts.get(part.id());
            if (row != null) matched.add(part.id());
            var fixed = part.sections().stream().map(s -> updateSection(part.id(), s, sections, matched)).toList();
            var pool = part.subjectPool().stream().map(s -> updateSection(part.id(), s, sections, matched)).toList();
            var options = part.options().stream().map(o -> new ExamPartOption(o.id(), o.kind(), o.code(), o.name(),
                    o.chooseSubjects(), o.countsByPick(), o.sections().stream()
                            .map(s -> updateSection(part.id(), s, sections, matched)).toList(),
                    o.blocks(), o.expertConfirmed())).toList();
            Integer count = row != null && row.questionCount() != null ? row.questionCount() : part.questionCount();
            if (row != null && row.questionCount() != null && fixed.size() == 1
                    && part.subjectPool().isEmpty() && part.options().isEmpty()
                    && !sections.containsKey(part.id() + "\u0000" + fixed.getFirst().id())) {
                ExamSection s = fixed.getFirst();
                fixed = List.of(new ExamSection(s.id(), s.subjectCode(), count, s.knowledgeScope(), s.answerQuotas(),
                        s.matrix(), s.choiceGroup(), s.blocks(), s.topicQuotas()));
            }
            return new ExamPart(part.id(), part.order(), part.name(),
                    row != null && row.durationMinutes() != null ? row.durationMinutes() : part.durationMinutes(),
                    count, part.questionCountOnPaper(), part.questionCountRequired(), part.participation(),
                    row != null && row.maxPoints() != null ? row.maxPoints() : part.maxPoints(), fixed, options,
                    pool, part.codeSource());
        }).toList();
        if (matched.size() != parts.size() + sections.size()) throw invalid();
        return new ExamStructureDocument(result, document.examQuotas());
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

    static List<ExamOverviewRequest.Row> changes(ExamStructureDocument current, ExamStructureDocument baseline) {
        Map<String, ExamPart> original = new HashMap<>();
        baseline.parts().forEach(part -> original.put(part.id(), part));
        List<ExamOverviewRequest.Row> changes = new ArrayList<>();
        for (ExamPart part : current.parts()) {
            ExamPart before = original.get(part.id());
            if (before == null) continue;
            Integer count = java.util.Objects.equals(part.questionCount(), before.questionCount())
                    ? null : part.questionCount();
            Integer duration = java.util.Objects.equals(part.durationMinutes(), before.durationMinutes())
                    ? null : part.durationMinutes();
            var points = java.util.Objects.equals(part.maxPoints(), before.maxPoints()) ? null : part.maxPoints();
            if (count != null || duration != null || points != null) {
                changes.add(new ExamOverviewRequest.Row(part.id(), null, null, null, count, duration, points));
            }
            Map<String, ExamSection> sections = new HashMap<>();
            before.sections().forEach(s -> sections.put(s.id(), s));
            before.subjectPool().forEach(s -> sections.put(s.id(), s));
            before.options().forEach(o -> o.sections().forEach(s -> sections.put(s.id(), s)));
            List<ExamSection> actual = new ArrayList<>(part.sections());
            actual.addAll(part.subjectPool());
            part.options().forEach(o -> actual.addAll(o.sections()));
            for (ExamSection section : actual) {
                ExamSection old = sections.get(section.id());
                if (old != null && section.questionCount() != null
                        && !java.util.Objects.equals(section.questionCount(), old.questionCount())) {
                    changes.add(new ExamOverviewRequest.Row(part.id(), section.id(), null,
                            section.subjectCode(), section.questionCount(), null, null));
                }
            }
        }
        return changes;
    }

    static List<ExamOverviewRequest.Row> presentIn(ExamStructureDocument source,
            ExamStructureDocument destination, List<ExamOverviewRequest.Row> changes) {
        Map<String, ExamPart> originals = new HashMap<>();
        source.parts().forEach(part -> originals.put(part.id(), part));
        List<ExamOverviewRequest.Row> result = new ArrayList<>();
        for (var row : changes) {
            ExamPart original = originals.get(row.partId());
            if (original == null) continue;
            List<ExamPart> matches = destination.parts().stream()
                    .filter(part -> java.util.Objects.equals(part.id(), original.id())
                            || original.order() != null && java.util.Objects.equals(part.order(), original.order()))
                    .toList();
            if (matches.size() != 1) continue;
            ExamPart part = matches.getFirst();
            String sectionId = null;
            if (row.sectionId() != null) {
                List<ExamSection> candidates = new ArrayList<>(part.sections());
                candidates.addAll(part.subjectPool());
                part.options().forEach(option -> candidates.addAll(option.sections()));
                List<ExamSection> matching = candidates.stream()
                        .filter(section -> java.util.Objects.equals(section.id(), row.sectionId())
                                || java.util.Objects.equals(section.subjectCode(), row.subjectCode()))
                        .toList();
                if (matching.size() != 1) continue;
                sectionId = matching.getFirst().id();
            }
            result.add(new ExamOverviewRequest.Row(part.id(), sectionId, null, row.subjectCode(),
                    row.questionCount(), row.durationMinutes(), row.maxPoints()));
        }
        return result;
    }

    private static ExamSection updateSection(String partId, ExamSection section,
            Map<String, ExamOverviewRequest.Row> rows, Set<String> matched) {
        String key = partId + "\u0000" + section.id();
        var row = rows.get(key);
        if (row == null) return section;
        if (row.durationMinutes() != null || row.maxPoints() != null) throw invalid();
        matched.add(key);
        return new ExamSection(section.id(), section.subjectCode(),
                row.questionCount() != null ? row.questionCount() : section.questionCount(),
                section.knowledgeScope(), section.answerQuotas(), section.matrix(), section.choiceGroup(),
                section.blocks(), section.topicQuotas());
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
    }
}
