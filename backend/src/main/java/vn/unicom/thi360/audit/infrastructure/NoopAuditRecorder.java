package vn.unicom.thi360.audit.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;

public class NoopAuditRecorder implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(NoopAuditRecorder.class);

    @Override
    public void record(AuditEvent event) {
        if (event == null) {
            return;
        }
        log.atDebug()
                .addKeyValue("module", event.module())
                .addKeyValue("action", event.action())
                .addKeyValue("targetType", event.targetType())
                .addKeyValue("targetId", event.targetId())
                .addKeyValue("outcome", event.outcome())
                .log("Audit event dropped (NoopAuditRecorder active)");
    }
}
