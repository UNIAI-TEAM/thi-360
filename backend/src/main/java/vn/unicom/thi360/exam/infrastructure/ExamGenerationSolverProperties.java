package vn.unicom.thi360.exam.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import vn.unicom.thi360.exam.domain.solver.SolverLimits;

/**
 * Limits of paper generation (LT F5), separate from the check and preview limits of {@link ExamSolverProperties}.
 * Deliberately not exposed as a {@link SolverLimits} bean: the check and preview classes inject that type.
 */
@ConfigurationProperties(prefix = "thi360.exam.solver.generate")
public record ExamGenerationSolverProperties(
        @DefaultValue("30s") Duration deadline,
        @DefaultValue("200") int maxPaths,
        @DefaultValue("20000") int maxCandidatesPerBlock,
        @DefaultValue("8") int maxChoiceSubjects) {

    public ExamGenerationSolverProperties {
        // Validate even if generation is not used right after startup.
        new SolverLimits(maxPaths, maxCandidatesPerBlock, deadline, maxChoiceSubjects);
    }

    /** Limits for one solver call; {@code timeout} is what is left of the {@link #deadline()}. */
    public SolverLimits limits(Duration timeout) {
        return new SolverLimits(maxPaths, maxCandidatesPerBlock, timeout, maxChoiceSubjects);
    }
}
