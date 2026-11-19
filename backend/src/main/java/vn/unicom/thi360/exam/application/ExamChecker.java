package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Executes the configuration and source checks outside database transactions. */
public interface ExamChecker {
    /** deadline includes time already spent reading the snapshot and waiting for a worker. */
    Evaluation check(ExamCheckSnapshotReader.Snapshot snapshot, Instant deadline, Duration budget, ExamCheckScope scope);

    /**
     * {@code rulesEvaluated()} is true only when the run vouches for the current rule set, which takes two things:
     * {@code proofsTerminal}, every unit of the run reached a verdict (VALID or INVALID: a unit left unconfirmed by the
     * time budget, a path or a source limit says nothing about the paths that hold it, whatever their verdict; the unit
     * engine decides it before units fold into path verdicts), and {@code assemblyComplete}, the result was rendered
     * without a deadline or limit cutting a source read short. The two are carried apart: a path that is INVALID for
     * another reason, or that an earlier VALID path outweighs, must not hide either of them.
     */
    record Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units,
                      boolean proofsTerminal, boolean assemblyComplete) {
        public Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units) {
            this(result, witness, units, true, true);
        }

        public boolean rulesEvaluated() {
            return proofsTerminal && assemblyComplete;
        }

        Evaluation withUnits(List<ExamCheckUnitStore.Unit> replaced) {
            return new Evaluation(result, witness, replaced, proofsTerminal, assemblyComplete);
        }

        Evaluation withProofsTerminal(boolean terminal) {
            return new Evaluation(result, witness, units, terminal, assemblyComplete);
        }

        /** A render step the deadline or a limit cut short, outside the assembler (e.g. the topic evidence reads). */
        Evaluation withAssemblyCutShort() {
            return new Evaluation(result, witness, units, proofsTerminal, false);
        }
    }
}
