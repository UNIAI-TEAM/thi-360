package vn.unicom.thi360.audit.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditEvent(
        String module,
        String action,
        String targetType,
        String targetId,
        String targetLabel,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason,
        AuditOutcome outcome,
        String errorCode
) {

    public AuditEvent {
        before = before == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        after = after == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(after));
        outcome = outcome == null ? AuditOutcome.SUCCESS : outcome;
    }

    public static AuditEvent success(
            String module,
            String action,
            String targetType,
            String targetId,
            String targetLabel) {
        return new AuditEvent(
                module, action, targetType, targetId, targetLabel,
                Map.of(), Map.of(), null, AuditOutcome.SUCCESS, null);
    }

    public static AuditEvent success(
            String module,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            Map<String, Object> after) {
        return new AuditEvent(
                module, action, targetType, targetId, targetLabel,
                Map.of(), after, null, AuditOutcome.SUCCESS, null);
    }

    public static AuditEvent failure(
            String module,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            String errorCode) {
        return new AuditEvent(
                module, action, targetType, targetId, targetLabel,
                Map.of(), Map.of(), null, AuditOutcome.FAILURE, errorCode);
    }
}
