package vn.unicom.thi360.audit.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
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
    public AuditEventView {
        actorRoles = actorRoles == null ? List.of() : List.copyOf(actorRoles);
        before = before == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        after = after == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(after));
    }
}
