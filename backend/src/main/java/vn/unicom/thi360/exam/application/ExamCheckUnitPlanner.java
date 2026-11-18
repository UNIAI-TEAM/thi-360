package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.solver.ChoiceGroupAllocator;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;

/** Finds the distinct sets of same-subject sections that can occur together on a path. */
final class ExamCheckUnitPlanner {
    record Key(String subjectCode, List<String> sectionIds) {
        Key { sectionIds = List.copyOf(sectionIds); }
    }
    record Unit(Key key, List<SectionOnPath> sections) {
        Unit { sections = List.copyOf(sections); }
    }
    private final int maxPaths;
    private final int maxChoiceSubjects;
    private final Map<Key, Unit> units = new LinkedHashMap<>();
    private final ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
    private int paths;

    private ExamCheckUnitPlanner(int maxPaths, int maxChoiceSubjects) {
        this.maxPaths = maxPaths;
        this.maxChoiceSubjects = maxChoiceSubjects;
    }

    static List<Unit> plan(List<SolvePart> parts, int maxPaths, int maxChoiceSubjects) {
        var planner = new ExamCheckUnitPlanner(maxPaths, maxChoiceSubjects);
        planner.part(parts, 0, new ArrayList<>());
        return List.copyOf(planner.units.values());
    }

    private void part(List<SolvePart> parts, int index, List<SectionOnPath> sections) {
        if (index == parts.size()) {
            if (++paths > maxPaths) throw new TooManyPaths();
            Map<String, List<SectionOnPath>> subjects = new LinkedHashMap<>();
            for (SectionOnPath section : sections) {
                subjects.computeIfAbsent(section.subjectCode(), ignored -> new ArrayList<>()).add(section);
            }
            for (var subject : subjects.entrySet()) {
                subject.getValue().sort(Comparator.comparing(SectionOnPath::sectionId));
                var key = new Key(subject.getKey(), subject.getValue().stream().map(SectionOnPath::sectionId).toList());
                units.putIfAbsent(key, new Unit(key, subject.getValue()));
            }
            return;
        }
        SolvePart current = parts.get(index);
        if (current instanceof SolvePart.Fixed fixed) {
            int size = sections.size();
            sections.addAll(fixed.sections());
            part(parts, index + 1, sections);
            sections.subList(size, sections.size()).clear();
        } else {
            SolvePart.Choice choice = (SolvePart.Choice) current;
            int size = sections.size();
            sections.addAll(choice.commonSections());
            options(parts, index, choice, 0, choice.choose(), sections);
            sections.subList(size, sections.size()).clear();
        }
    }

    private void options(List<SolvePart> parts, int partIndex, SolvePart.Choice choice,
            int start, int remaining, List<SectionOnPath> sections) {
        if (remaining == 0) {
            part(parts, partIndex + 1, sections);
            return;
        }
        for (int i = start; i <= choice.options().size() - remaining; i++) {
            SolveOption option = choice.options().get(i);
            if (option instanceof SolveOption.Sections selected) {
                int size = sections.size();
                sections.addAll(selected.sections());
                options(parts, partIndex, choice, i + 1, remaining - 1, sections);
                sections.subList(size, sections.size()).clear();
            } else {
                SubjectChoice selected = (SubjectChoice) option;
                if (selected.sections().size() > maxChoiceSubjects) throw new TooManyChoiceSubjects();
                if (selected.pickCount() > maxPaths) throw new TooManyPaths();
                for (var pick : selected.picks(allocator)) {
                    int size = sections.size();
                    sections.addAll(pick.resolvedSections());
                    options(parts, partIndex, choice, i + 1, remaining - 1, sections);
                    sections.subList(size, sections.size()).clear();
                }
            }
        }
    }

    static final class TooManyPaths extends RuntimeException { }
    static final class TooManyChoiceSubjects extends RuntimeException { }
}
