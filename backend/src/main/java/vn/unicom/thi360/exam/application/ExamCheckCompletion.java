package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditActorType;

/** Short compare-and-write transaction for sync and background checks. */
@Service
public class ExamCheckCompletion {
    private final JdbcTemplate jdbc;
    private final ExamCheckResultStore results;
    private final ExamCheckJobStore jobs;
    private final ExamCheckAudit audit;
    private final ObjectMapper mapper;

    public ExamCheckCompletion(JdbcTemplate jdbc, ExamCheckResultStore results, ExamCheckJobStore jobs,
            ExamCheckAudit audit, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.results = results;
        this.jobs = jobs;
        this.audit = audit;
        this.mapper = mapper;
    }

    public record Actor(UUID id, String username, AuditActorType type) { }
    public record Completion(ObjectNode result, boolean accepted) { }

    @Transactional
    public Completion complete(ExamCheckResultStore.Input input, UUID runId, ObjectNode result,
            JsonNode witness, Instant checkedAt, long durationMs, Actor actor, String traceId) {
        // The same order as startOrGet: exam first, then job, avoiding a claim/completion deadlock.
        if (runId != null) {
            List<UUID> exam = jdbc.queryForList("SELECT id FROM exam.exam WHERE id = ? FOR UPDATE",
                    UUID.class, input.examId());
            if (exam.isEmpty()) return new Completion(result, false);
            List<String> states = jdbc.queryForList("SELECT state FROM exam.exam_check_job "
                    + "WHERE id = ? AND exam_id = ? FOR UPDATE", String.class, runId, input.examId());
            if (states.isEmpty() || !"RUNNING".equals(states.getFirst())) {
                return new Completion(result, false);
            }
        }
        ObjectNode completed = result.deepCopy();
        completed.put("stale", false);
        boolean saved = results.save(input, completed.path("status").asText(), completed.path("issueCount").asInt(),
                checkedAt, durationMs, completed, witness);
        completed.put("stale", !saved);
        if (runId != null && !jobs.complete(runId, json(completed))) {
            throw new IllegalStateException("Running check job changed during completion");
        }
        audit.record(input.examId(), "EXAM.CHECK",
                Map.of("status", completed.path("status").asText(), "stale", !saved), actor, traceId);
        return new Completion(completed, true);
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize check result", e);
        }
    }
}
