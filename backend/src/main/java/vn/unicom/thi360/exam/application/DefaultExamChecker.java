package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.SolveMode;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStats;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Runs exact configuration checks and the shared solver without holding a database transaction. */
@Service
public class DefaultExamChecker implements ExamChecker {
    private final SolverInputBuilder builder;
    private final QuestionSourcePort source;
    private final SolverLimits limits;
    private final Clock clock;
    private final ObjectMapper mapper;

    public DefaultExamChecker(SolverInputBuilder builder, QuestionSourcePort source, SolverLimits limits,
                              Clock clock, ObjectMapper mapper) {
        this.builder = builder;
        this.source = source;
        this.limits = limits;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public Evaluation check(ExamCheckSnapshotReader.Snapshot snapshot, Instant deadline, Duration budget) {
        var rules = new ExamCheckRules(mapper, snapshot.exam(), snapshot.structure());
        List<ObjectNode> common = new ArrayList<>(rules.common());
        List<ObjectNode> invalid = rules.unrepresentable();
        var assembler = new ExamCheckAssembler(mapper, clock, limits, source);
        if (!invalid.isEmpty()) {
            common.addAll(invalid);
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        if (!clock.instant().isBefore(deadline)) {
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        SolverInputBuilder.SolverInput input = builder.build(snapshot.exam().getId(), snapshot.structure());
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative()) remaining = Duration.ZERO;
        SolverLimits bounded = new SolverLimits(limits.maxPaths(), limits.maxCandidatesPerBlock(),
                remaining, limits.maxChoiceSubjects());
        SolveOutcome outcome = new PaperSolver(bounded, clock).solveAll(input.parts(), input.candidates(), SolveMode.check());
        return assembler.assemble(snapshot.exam(), snapshot.structure(), input, outcome, deadline, budget, common, rules::path);
    }
}
