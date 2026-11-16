package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.SolveMode;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStats;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;

/** Runs exact configuration checks and the shared solver without holding a database transaction. */
@Service
public class DefaultExamChecker implements ExamChecker {
    private final SolverInputBuilder builder;
    private final SolverLimits limits;
    private final Clock clock;
    private final ObjectMapper mapper;

    public DefaultExamChecker(SolverInputBuilder builder, SolverLimits limits,
                              Clock clock, ObjectMapper mapper) {
        this.builder = builder;
        this.limits = limits;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public Evaluation check(ExamCheckSnapshotReader.Snapshot snapshot, Instant deadline, Duration budget) {
        var rules = new ExamCheckRules(mapper, snapshot.exam(), snapshot.structure());
        List<ObjectNode> common = new ArrayList<>(rules.common());
        List<ObjectNode> invalid = rules.unrepresentable();
        var assembler = new ExamCheckAssembler(mapper, clock, limits);
        Map<String, List<ObjectNode>> invalidOptions = new LinkedHashMap<>();
        boolean commonInvalid = false;
        for (ObjectNode issue : invalid) {
            String optionId = issue.path("optionId").asText(null);
            if (optionId == null) commonInvalid = true;
            else invalidOptions.computeIfAbsent(optionId, ignored -> new ArrayList<>()).add(issue);
        }
        if (commonInvalid) {
            common.addAll(invalid);
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        Function<PathResult, List<ObjectNode>> pathIssues = path -> {
            List<ObjectNode> found = new ArrayList<>(rules.path(path));
            path.path().chosen().forEach(choice ->
                    found.addAll(invalidOptions.getOrDefault(choice.optionId(), List.of())));
            return found;
        };
        if (!clock.instant().isBefore(deadline)) {
            common.addAll(invalid);
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        ExamStructureDocument solverDocument = invalidOptions.isEmpty() ? snapshot.structure()
                : maskInvalidOptions(snapshot.structure(), invalidOptions.keySet());
        SolverInputBuilder.SolverInput input = builder.build(snapshot.exam().getId(), solverDocument);
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative()) remaining = Duration.ZERO;
        SolverLimits bounded = new SolverLimits(limits.maxPaths(), limits.maxCandidatesPerBlock(),
                remaining, limits.maxChoiceSubjects());
        SolveOutcome outcome = new PaperSolver(bounded, clock).solveAll(input.parts(), input.candidates(), SolveMode.check());
        return assembler.assemble(snapshot.exam(), snapshot.structure(), input, outcome, deadline, budget, common, pathIssues);
    }

    /** Keep each invalid option's path identifiable while solving unrelated valid options normally. */
    private static ExamStructureDocument maskInvalidOptions(ExamStructureDocument document, Set<String> invalid) {
        List<ExamPart> parts = new ArrayList<>(document.parts().size());
        for (ExamPart part : document.parts()) {
            List<ExamPartOption> options = new ArrayList<>(part.options().size());
            for (ExamPartOption option : part.options()) {
                options.add(invalid.contains(option.id())
                        ? new ExamPartOption(option.id(), OptionKind.SUBJECT, option.code(), option.name(),
                                null, null, List.of(), List.of(), option.expertConfirmed())
                        : option);
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                    part.questionCountOnPaper(), part.questionCountRequired(), part.participation(),
                    part.maxPoints(), part.sections(), options));
        }
        return new ExamStructureDocument(parts);
    }
}
