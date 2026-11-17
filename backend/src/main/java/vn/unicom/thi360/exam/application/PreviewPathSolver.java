package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveMode;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;

/** Uses the solver's generation entrypoint for choices requiring a post-solve trim. */
final class PreviewPathSolver {
    record Result(PathResult path, List<QuestionPick> dropped) {}

    static Result solve(PaperSolver solver, SolvePath path, CandidateSource source, long seed) {
        boolean needsTrim = path.choices().stream().anyMatch(choice ->
                choice.countsByPick().stream().distinct().count() > 1);
        if (!needsTrim) {
            return new Result(solver.solvePath(path, source, SolveMode.generate(seed)), List.of());
        }

        PathResult fixed = solver.solvePath(SolvePath.of(path.pathIndex(), path.sections()), source,
                SolveMode.generate(seed));
        if (fixed.status() != SolveStatus.VALID) return new Result(fixed, List.of());
        List<SubjectWitness> witnesses = new ArrayList<>(fixed.witness());
        List<QuestionPick> dropped = new ArrayList<>();
        Set<String> used = new HashSet<>();
        addUsed(used, fixed.witness(), path.sections(), source);
        List<List<SubjectPick>> picks = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            SubjectPick selected = path.picks().get(i).getFirst();
            PathResult result = solver.solvePick(choice, selected.subjectCodes(), seed, source, used);
            if (result.status() != SolveStatus.VALID) return new Result(result, List.of());
            witnesses.addAll(result.witness());
            picks.add(result.path().picks().getFirst());
            if (result.droppedQuestion() != null) dropped.add(result.droppedQuestion());
            addUsed(used, result.witness(), result.path().picks().getFirst().getFirst().resolvedSections(), source);
        }
        SolvePath actual = new SolvePath(path.pathIndex(), path.chosen(), path.sections(), path.choices(), picks);
        return new Result(new PathResult(actual, SolveStatus.VALID, null, null, null, witnesses,
                List.of(), null), List.copyOf(dropped));
    }

    private static void addUsed(Set<String> used, List<SubjectWitness> witnesses,
            List<SectionOnPath> sections, CandidateSource source) {
        Set<String> roots = new HashSet<>();
        for (SubjectWitness witness : witnesses) {
            for (GroupWitness group : witness.groups()) {
                for (QuestionPick pick : group.picks()) roots.add(pick.questionId());
            }
        }
        used.addAll(roots);
        for (SectionOnPath section : sections) {
            for (GroupQuota group : section.groups()) {
                var candidates = source.candidatesFor(group);
                if (candidates == null) continue;
                for (var candidate : candidates) {
                    if (!roots.contains(candidate.questionId())) continue;
                    for (var unit : candidate.units()) {
                        if (unit.childId() != null) used.add(unit.childId());
                    }
                }
            }
        }
    }
}
