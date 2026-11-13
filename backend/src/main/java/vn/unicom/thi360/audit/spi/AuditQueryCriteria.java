package vn.unicom.thi360.audit.spi;

import java.time.Instant;
import java.util.UUID;

public record AuditQueryCriteria(
        String targetType,
        String targetId,
        UUID actorId,
        String action,
        Instant from,
        Instant to
) {
    public static AuditQueryCriteria forTarget(String targetType, String targetId, Instant from, Instant to) {
        return new AuditQueryCriteria(targetType, targetId, null, null, from, to);
    }
}
