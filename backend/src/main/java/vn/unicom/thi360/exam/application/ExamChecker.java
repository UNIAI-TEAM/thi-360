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

    record Evaluation(ObjectNode result, JsonNode witness, List<ExamCheckUnitStore.Unit> units) { }
}
