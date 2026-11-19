package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.infrastructure.ExamGenerationSolverProperties;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * {@link PaperSolverPort} on the wave 2B solver. It reuses the preview path (published combinations, path resolver,
 * {@code solvePartExcluding} / {@code solvePick} with trim) with its own limits and no preview side effects: nothing
 * is written, nothing is ordered. Not transactional; each read opens its own short transaction.
 */
@Service
public class PaperGenerationService implements PaperSolverPort {
    private final ExamPreviewStore store;
    private final SolverInputBuilder builder;
    private final ExamGenerationSolverProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PaperGenerationService(ExamPreviewStore store, SolverInputBuilder builder,
            ExamGenerationSolverProperties properties, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.builder = builder;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public GeneratedPaper generate(UUID examId, int versionNo, List<String> partCodes, List<Selection> selections,
            long seed, Set<UUID> excludeQuestionIds) {
        Instant deadline = clock.instant().plus(properties.deadline());
        try {
            return generate(examId, versionNo, partCodes, selections, seed, excludeQuestionIds, deadline);
        } catch (PaperGenerationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PaperGenerationException(Reason.GENERATION_FAILED,
                    "Generation failed: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private GeneratedPaper generate(UUID examId, int versionNo, List<String> partCodes, List<Selection> selections,
            long seed, Set<UUID> excludeQuestionIds, Instant deadline) {
        if (examId == null) throw PaperGenerationPlan.failed("examId is missing");
        ExamPreviewStore.Snapshot snapshot;
        try {
            snapshot = store.loadVersion(examId, versionNo);
        } catch (NotFoundException ex) {
            throw PaperGenerationPlan.failed("exam version not found");
        }
        ExamStructureDocument document;
        try {
            document = PreviewPublishedCombinations.project(
                    new ExamStructureCodec(mapper).read(snapshot.parts()), snapshot.publishedCombinations());
        } catch (BusinessRuleException ex) {
            throw PaperGenerationPlan.failed("version snapshot is not usable");
        }
        PaperGenerationPlan.Plan plan = PaperGenerationPlan.of(document, partCodes, selections);

        SolverLimits limits = properties.limits(remaining(deadline));
        PaperSolver solver = new PaperSolver(limits, clock);
        SolverInputBuilder.SolverInput input;
        PreviewPathResolver.Resolved resolved;
        try {
            input = builder.build(snapshot.exam().getKindItemId(), plan.document(), limits);
            resolved = PreviewPathResolver.resolve(plan.document(), input.parts(), plan.pathId(),
                    plan.subjectCodes(), seed, solver.allocator());
        } catch (IllegalArgumentException | NotFoundException | BusinessRuleException ex) {
            throw PaperGenerationPlan.failed("selection or structure is not valid: " + ex.getMessage());
        }

        Set<String> excluded = new HashSet<>();
        if (excludeQuestionIds != null) excludeQuestionIds.forEach(id -> excluded.add(id.toString()));
        PreviewPathSolver.Result solved = PreviewPathSolver.solve(solver, resolved.path(), input.candidates(),
                seed, excluded);
        PathResult result = solved.path();
        if (result.status() != SolveStatus.VALID) {
            boolean timeout = result.reason() == SolveReason.DEADLINE_EXCEEDED;
            throw new PaperGenerationException(timeout ? Reason.GENERATION_TIMEOUT : Reason.GENERATION_FAILED,
                    "Solver " + result.status() + (result.reason() == null ? "" : " " + result.reason())
                            + (result.subjectCode() == null ? "" : " subject " + result.subjectCode())
                            + (result.groupId() == null ? "" : " group " + result.groupId()));
        }
        if (clock.instant().isAfter(deadline)) {
            throw new PaperGenerationException(Reason.GENERATION_TIMEOUT, "Generation ran past its deadline");
        }
        return PaperGenerationAssembler.assemble(plan.partIds(), input.parts(), result.path(), result.witness(),
                solved.dropped(), input.candidates().loadedCandidates());
    }

    private Duration remaining(Instant deadline) {
        Duration left = Duration.between(clock.instant(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
