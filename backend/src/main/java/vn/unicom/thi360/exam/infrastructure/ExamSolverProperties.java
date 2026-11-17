package vn.unicom.thi360.exam.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import vn.unicom.thi360.exam.domain.solver.SolverLimits;

@ConfigurationProperties(prefix = "thi360.exam.solver")
public record ExamSolverProperties(
        @DefaultValue("200") int maxPaths,
        @DefaultValue("20000") int maxCandidatesPerBlock,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("8") int maxChoiceSubjects) {

    public ExamSolverProperties {
        // Validate even if the solver is not used immediately after startup.
        new SolverLimits(maxPaths, maxCandidatesPerBlock, timeout, maxChoiceSubjects);
    }

    public SolverLimits toLimits() {
        return new SolverLimits(maxPaths, maxCandidatesPerBlock, timeout, maxChoiceSubjects);
    }
}
