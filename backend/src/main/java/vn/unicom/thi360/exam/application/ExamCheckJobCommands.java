package vn.unicom.thi360.exam.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims and failures share a transaction with their audit event. */
@Service
public class ExamCheckJobCommands {
    private final ExamCheckJobStore jobs;
    private final ExamCheckAudit audit;

    ExamCheckJobCommands(ExamCheckJobStore jobs, ExamCheckAudit audit) {
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public ExamCheckJobStore.Start start(UUID examId, ExamCheckCompletion.Actor actor, String traceId) {
        ExamCheckJobStore.Start start = jobs.startOrGet(examId, actor.id());
        if (start.created()) {
            audit.record(examId, "EXAM.CHECK_START", Map.of("runId", start.runId().toString()), actor, traceId);
        }
        return start;
    }

    @Transactional
    public void fail(UUID examId, UUID runId, String code, ExamCheckCompletion.Actor actor, String traceId) {
        if (jobs.fail(runId, code)) {
            audit.record(examId, "EXAM.CHECK_FAILED", Map.of("runId", runId.toString(), "errorCode", code),
                    actor, traceId);
        }
    }
}
