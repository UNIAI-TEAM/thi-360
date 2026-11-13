package vn.unicom.thi360.audit.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditOutcome;

public record AuditOutboxPayload(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String actorUsername,
        List<String> actorRoles,
        AuditActorType actorType,
        String module,
        String action,
        String targetType,
        String targetId,
        String targetLabel,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason,
        AuditOutcome outcome,
        String errorCode,
        String traceId,
        String ip
) {
}
