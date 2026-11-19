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
     * {@code rulesEvaluated} is true only when every unit of the run reached a verdict (VALID or INVALID): a unit left
     * unconfirmed (time budget, path or source limit) means the result cannot vouch for the current rule set, whatever
     * the verdict of the paths that hold it. The unit engine decides it before units fold into path verdicts.
     */
    record Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units, boolean rulesEvaluated) {
        public Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units) {
            this(result, witness, units, true);
        }

        Evaluation withUnits(List<ExamCheckUnitStore.Unit> replaced) {
            return new Evaluation(result, witness, replaced, rulesEvaluated);
        }

        Evaluation withRulesEvaluated(boolean evaluated) {
            return new Evaluation(result, witness, units, evaluated);
        }
    }
}
