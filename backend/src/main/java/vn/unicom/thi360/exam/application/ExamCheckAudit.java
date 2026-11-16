package vn.unicom.thi360.exam.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
class ExamCheckAudit {
    private final AuditRecorder audit;

    ExamCheckAudit(AuditRecorder audit) {
        this.audit = audit;
    }

    void record(UUID examId, String action, Map<String, Object> after,
                ExamCheckCompletion.Actor actor, String traceId) {
        String previous = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        try {
            if (traceId == null || traceId.isBlank()) MDC.remove(TraceIdFilter.MDC_TRACE_ID);
            else MDC.put(TraceIdFilter.MDC_TRACE_ID, traceId);
            audit.record(AuditEvent.builder("EXAM", action).target("EXAM", examId.toString(), null)
                    .actor(actor.id(), actor.username(), List.of(), actor.type()).after(after).build());
        } finally {
            if (previous == null) MDC.remove(TraceIdFilter.MDC_TRACE_ID);
            else MDC.put(TraceIdFilter.MDC_TRACE_ID, previous);
        }
    }
}
