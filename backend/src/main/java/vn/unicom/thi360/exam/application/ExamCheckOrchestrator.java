package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/** Runs checks outside transactions and commits only the finished result in a short transaction. */
@Service
public class ExamCheckOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ExamCheckOrchestrator.class);
    private final ExamCheckSnapshotReader snapshots;
    private final ExamChecker checker;
    private final ExamCheckCompletion completion;
    private final ExamCheckJobCommands commands;
    private final Executor executor;
    private final SolverLimits limits;
    private final Clock clock;
    private final ObjectMapper mapper;

    public ExamCheckOrchestrator(ExamCheckSnapshotReader snapshots, ExamChecker checker,
            ExamCheckCompletion completion, ExamCheckJobCommands commands,
            @Qualifier("examCheckExecutor") Executor executor, SolverLimits limits,
            Clock clock, ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.checker = checker;
        this.completion = completion;
        this.commands = commands;
        this.executor = executor;
        this.limits = limits;
        this.clock = clock;
        this.mapper = mapper;
    }

    public ObjectNode check(UUID examId, ExamCheckCompletion.Actor actor, String traceId) {
        Instant started = clock.instant();
        return execute(examId, null, actor, traceId, started, limits.timeout()).result();
    }

    public UUID startDeep(UUID examId, ExamCheckCompletion.Actor actor, String traceId) {
        Instant started = clock.instant();
        ExamCheckJobStore.Start start = commands.start(examId, actor, traceId);
        if (start.created()) {
            try {
                executor.execute(() -> runDeep(examId, start.runId(), actor, traceId, started));
            } catch (RuntimeException e) {
                commands.fail(examId, start.runId(), "CHECK_RUN_FAILED", actor, traceId);
                throw e;
            }
        }
        return start.runId();
    }

    public record Run(UUID runId, String state, JsonNode result, String errorCode) { }

    public Run run(UUID examId, UUID runId, ExamCheckCompletion.Actor actor, String traceId) {
        ExamCheckJobStore.Job job = commands.get(examId, runId, actor, traceId);
        try {
            return new Run(job.runId(), job.state(), job.resultJson() == null ? null : mapper.readTree(job.resultJson()),
                    job.errorCode());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid persisted check result", e);
        }
    }

    private void runDeep(UUID examId, UUID runId, ExamCheckCompletion.Actor actor, String traceId, Instant started) {
        String previousTrace = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String previousUser = MDC.get(TraceIdFilter.MDC_USER_ID);
        try {
            if (traceId == null || traceId.isBlank()) MDC.remove(TraceIdFilter.MDC_TRACE_ID);
            else MDC.put(TraceIdFilter.MDC_TRACE_ID, traceId);
            if (actor.id() == null) MDC.remove(TraceIdFilter.MDC_USER_ID);
            else MDC.put(TraceIdFilter.MDC_USER_ID, actor.id().toString());
            if (!"RUNNING".equals(commands.get(examId, runId, actor, traceId).state())) return;
            execute(examId, runId, actor, traceId, started, Duration.ofSeconds(120));
        } catch (RuntimeException e) {
            log.error("Deep check failed for exam {} and run {}", examId, runId, e);
            commands.fail(examId, runId, "CHECK_RUN_FAILED", actor, traceId);
        } finally {
            restore(TraceIdFilter.MDC_TRACE_ID, previousTrace);
            restore(TraceIdFilter.MDC_USER_ID, previousUser);
        }
    }

    private ExamCheckCompletion.Completion execute(UUID examId, UUID runId, ExamCheckCompletion.Actor actor,
            String traceId, Instant started, Duration budget) {
        ExamCheckSnapshotReader.Snapshot snapshot = snapshots.read(examId);
        ExamChecker.Evaluation evaluation = checker.check(snapshot, started.plus(budget), budget);
        Instant finished = clock.instant();
        long durationMs = Math.max(0, Duration.between(started, finished).toMillis());
        ObjectNode result = evaluation.result().deepCopy();
        result.put("examId", examId.toString());
        result.put("checkedAt", finished.toString());
        result.put("durationSeconds", durationMs / 1000.0);
        result.put("timeLimitSeconds", budget.toSeconds());
        result.putNull("invalidReason");
        return completion.complete(snapshot.persistenceInput(), runId, result, evaluation.witness(),
                finished, durationMs, actor, traceId);
    }

    private static void restore(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }
}
