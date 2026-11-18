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
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.TrimPolicy;

/** Uses the solver's generation entrypoint for choices requiring a post-solve trim. */
final class PreviewPathSolver {
    record Result(PathResult path, List<QuestionPick> dropped) {}

    static Result solve(PaperSolver solver, SolvePath path, CandidateSource source, long seed) {
        return solve(solver, path, source, seed, Set.of());
    }

    static Result solve(PaperSolver solver, SolvePath path, CandidateSource source, long seed,
            Set<String> excluded) {
        boolean needsTrim = path.choices().stream().anyMatch(choice ->
                choice.countsByPick().stream().distinct().count() > 1);
        if (!needsTrim) {
            return new Result(solver.solvePartExcluding(path, source, SolveMode.generate(seed), excluded), List.of());
        }

        List<SubjectChoice> jointChoices = new ArrayList<>();
        List<List<SubjectPick>> jointPicks = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (choice.countsByPick().stream().distinct().count() == 1) {
                jointChoices.add(choice);
                jointPicks.add(path.picks().get(i));
            }
        }
        SolvePath jointPath = new SolvePath(path.pathIndex(), path.chosen(), path.sections(), jointChoices, jointPicks);
        PathResult joint = solver.solvePartExcluding(jointPath, source, SolveMode.generate(seed), excluded);
        if (joint.status() != SolveStatus.VALID) return new Result(joint, List.of());
        List<SubjectWitness> witnesses = new ArrayList<>(joint.witness());
        List<QuestionPick> dropped = new ArrayList<>();
        Set<String> used = new HashSet<>(excluded);
        List<SectionOnPath> jointSections = new ArrayList<>(path.sections());
        for (List<SubjectPick> picks : jointPicks) {
            for (SubjectPick pick : picks) jointSections.addAll(pick.resolvedSections());
        }
        addUsed(used, joint.witness(), jointSections, source);
        List<List<SubjectPick>> picks = new ArrayList<>(path.picks());
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (choice.countsByPick().stream().distinct().count() == 1) continue;
            SubjectPick selected = path.picks().get(i).getFirst();
            PathResult result = solver.solvePick(choice, selected.subjectCodes(), seed, source, used);
            if (result.status() != SolveStatus.VALID) return new Result(result, List.of());
            witnesses.addAll(result.witness());
            picks.set(i, result.path().picks().getFirst());
            if (result.droppedQuestion() != null) dropped.add(result.droppedQuestion());
            addUsed(used, result.witness(), result.path().picks().getFirst().getFirst().resolvedSections(), source);
        }
        SolvePath actual = new SolvePath(path.pathIndex(), path.chosen(), path.sections(), path.choices(), picks);
        return new Result(new PathResult(actual, SolveStatus.VALID, null, null, null, witnesses,
                List.of(), null), List.copyOf(dropped));
    }

    static Result solveSection(PaperSolver solver, SolvePath path, CandidateSource source, long seed,
            boolean trim) {
        PathResult full = solver.solvePath(path, source, SolveMode.generate(seed));
        if (!trim || full.status() != SolveStatus.VALID) return new Result(full, List.of());
        SectionOnPath section = path.sections().getFirst();
        SubjectWitness witness = full.witness().getFirst();
        TrimPolicy.Result trimmed = TrimPolicy.trim(section, witness, source, seed);
        if (trimmed == null) {
            return new Result(new PathResult(path, SolveStatus.INVALID, SolveReason.NO_JOINT_SOLUTION,
                    section.subjectCode(), null, List.of(), List.of(), null), List.of());
        }
        SolvePath actual = SolvePath.of(path.pathIndex(), List.of(section.withQuestionCount(section.questionCount() - 1)));
        return new Result(new PathResult(actual, SolveStatus.VALID, null, null, null,
                List.of(trimmed.witness()), List.of(), null), List.of(trimmed.droppedQuestion()));
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
