package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.solver.ChosenOption;
import vn.unicom.thi360.exam.domain.solver.ChoiceGroupAllocator;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;

/** Finds the distinct sets of same-subject sections that can occur together on a path. */
final class ExamCheckUnitPlanner {
    record Key(String subjectCode, List<String> sectionIds) {
        Key { sectionIds = List.copyOf(sectionIds); }
    }
    record Unit(Key key, List<SectionOnPath> sections) {
        Unit { sections = List.copyOf(sections); }
    }
    record Tuple(List<SubjectPick> picks, List<Key> units) {
        Tuple { picks = List.copyOf(picks); units = List.copyOf(units); }
    }
    record Path(SolvePath path, List<Tuple> tuples) {
        Path { tuples = List.copyOf(tuples); }
    }
    record Plan(List<Unit> units, List<Path> paths) { }
    private final int maxPaths;
    private final int maxChoiceSubjects;
    private final Map<Key, Unit> units = new LinkedHashMap<>();
    private final Map<List<ChosenOption>, PathBuilder> pathBuilders = new LinkedHashMap<>();
    private final ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
    private int paths;

    private ExamCheckUnitPlanner(int maxPaths, int maxChoiceSubjects) {
        this.maxPaths = maxPaths;
        this.maxChoiceSubjects = maxChoiceSubjects;
    }

    static Plan plan(List<SolvePart> parts, int maxPaths, int maxChoiceSubjects) {
        var planner = new ExamCheckUnitPlanner(maxPaths, maxChoiceSubjects);
        planner.part(parts, 0, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        List<Path> paths = new ArrayList<>();
        for (PathBuilder builder : planner.pathBuilders.values()) paths.add(builder.build(paths.size()));
        return new Plan(List.copyOf(planner.units.values()), List.copyOf(paths));
    }

    private void part(List<SolvePart> parts, int index, List<SectionOnPath> sections,
            List<ChosenOption> chosen, List<SubjectChoice> choices, List<SubjectPick> picks) {
        if (index == parts.size()) {
            if (++paths > maxPaths) throw new TooManyPaths();
            List<Key> keys = new ArrayList<>();
            Map<String, List<SectionOnPath>> subjects = new LinkedHashMap<>();
            for (SectionOnPath section : sections) {
                subjects.computeIfAbsent(section.subjectCode(), ignored -> new ArrayList<>()).add(section);
            }
            for (var subject : subjects.entrySet()) {
                subject.getValue().sort(Comparator.comparing(SectionOnPath::sectionId));
                var key = new Key(subject.getKey(), subject.getValue().stream().map(SectionOnPath::sectionId).toList());
                keys.add(key);
                units.putIfAbsent(key, new Unit(key, subject.getValue()));
            }
            List<SectionOnPath> fixed = new ArrayList<>(sections);
            for (SubjectPick pick : picks) {
                for (SectionOnPath selected : pick.resolvedSections()) {
                    fixed.remove(selected);
                }
            }
            List<ChosenOption> chosenKey = List.copyOf(chosen);
            PathBuilder builder = pathBuilders.computeIfAbsent(chosenKey,
                    ignored -> new PathBuilder(chosenKey, List.copyOf(fixed), List.copyOf(choices)));
            builder.add(new Tuple(picks, keys));
            return;
        }
        SolvePart current = parts.get(index);
        if (current instanceof SolvePart.Fixed fixed) {
            int size = sections.size();
            sections.addAll(fixed.sections());
            part(parts, index + 1, sections, chosen, choices, picks);
            sections.subList(size, sections.size()).clear();
        } else {
            SolvePart.Choice choice = (SolvePart.Choice) current;
            int size = sections.size();
            sections.addAll(choice.commonSections());
            options(parts, index, choice, 0, choice.choose(), sections, chosen, choices, picks);
            sections.subList(size, sections.size()).clear();
        }
    }

    private void options(List<SolvePart> parts, int partIndex, SolvePart.Choice choice,
            int start, int remaining, List<SectionOnPath> sections, List<ChosenOption> chosen,
            List<SubjectChoice> choices, List<SubjectPick> picks) {
        if (remaining == 0) {
            part(parts, partIndex + 1, sections, chosen, choices, picks);
            return;
        }
        for (int i = start; i <= choice.options().size() - remaining; i++) {
            SolveOption option = choice.options().get(i);
            String optionId = option instanceof SolveOption.Sections value
                    ? value.optionId() : ((SubjectChoice) option).optionId();
            chosen.add(new ChosenOption(choice.partId(), optionId));
            if (option instanceof SolveOption.Sections selected) {
                int size = sections.size();
                sections.addAll(selected.sections());
                options(parts, partIndex, choice, i + 1, remaining - 1, sections, chosen, choices, picks);
                sections.subList(size, sections.size()).clear();
            } else {
                SubjectChoice selected = (SubjectChoice) option;
                if (selected.sections().size() > maxChoiceSubjects) throw new TooManyChoiceSubjects();
                if (selected.pickCount() > maxPaths) throw new TooManyPaths();
                choices.add(selected);
                for (var pick : selected.picks(allocator)) {
                    int size = sections.size();
                    sections.addAll(pick.resolvedSections());
                    picks.add(pick);
                    options(parts, partIndex, choice, i + 1, remaining - 1, sections, chosen, choices, picks);
                    picks.removeLast();
                    sections.subList(size, sections.size()).clear();
                }
                choices.removeLast();
            }
            chosen.removeLast();
        }
    }

    private static final class PathBuilder {
        private final List<ChosenOption> chosen;
        private final List<SectionOnPath> fixed;
        private final List<SubjectChoice> choices;
        private final List<Tuple> tuples = new ArrayList<>();

        PathBuilder(List<ChosenOption> chosen, List<SectionOnPath> fixed, List<SubjectChoice> choices) {
            this.chosen = chosen;
            this.fixed = fixed;
            this.choices = choices;
        }

        void add(Tuple tuple) { tuples.add(tuple); }

        Path build(long index) {
            List<List<SubjectPick>> picks = new ArrayList<>();
            for (int i = 0; i < choices.size(); i++) {
                int position = i;
                picks.add(tuples.stream().map(tuple -> tuple.picks().get(position)).distinct().toList());
            }
            return new Path(new SolvePath(index, chosen, fixed, choices, picks), tuples);
        }
    }

    static final class TooManyPaths extends RuntimeException { }
    static final class TooManyChoiceSubjects extends RuntimeException { }
}
