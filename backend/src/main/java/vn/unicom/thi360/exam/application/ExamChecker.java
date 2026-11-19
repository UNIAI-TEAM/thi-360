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
     * {@code rulesEvaluated} is false when the checker never evaluated the paths against the rules (the solve was
     * blocked by the time budget or the path limit), so the result cannot vouch for the current rule set.
     */
    record Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units, boolean rulesEvaluated) {
        public Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units) {
            this(result, witness, units, true);
        }

        Evaluation withUnits(List<ExamCheckUnitStore.Unit> replaced) {
            return new Evaluation(result, witness, replaced, rulesEvaluated);
        }
    }
}
