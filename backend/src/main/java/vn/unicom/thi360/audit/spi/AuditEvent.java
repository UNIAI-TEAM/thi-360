package vn.unicom.thi360.audit.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        String errorCode,
        UUID actorId,
        String actorUsername,
        List<String> actorRoles,
        AuditActorType actorType
) {
    public AuditEvent {
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        before = before == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        after = after == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(after));
        outcome = outcome == null ? AuditOutcome.SUCCESS : outcome;
        actorRoles = actorRoles == null ? null : List.copyOf(actorRoles);
    }

    public static AuditEvent of(
            String module,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason,
            AuditOutcome outcome,
            String errorCode) {
        return new AuditEvent(
                module,
                action,
                targetType,
                targetId,
                targetLabel,
                before,
                after,
                reason,
                outcome,
                errorCode,
                null,
                null,
                null,
                null);
    }

    public static Builder builder(String module, String action) {
        return new Builder(module, action);
    }

    public static final class Builder {
        private final String module;
        private final String action;
        private String targetType;
        private String targetId;
        private String targetLabel;
        private Map<String, Object> before;
        private Map<String, Object> after;
        private String reason;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private String errorCode;
        private UUID actorId;
        private String actorUsername;
        private List<String> actorRoles;
        private AuditActorType actorType;

        private Builder(String module, String action) {
            this.module = module;
            this.action = action;
        }

        public Builder target(String targetType, String targetId, String targetLabel) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetLabel = targetLabel;
            return this;
        }

        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder targetLabel(String targetLabel) {
            this.targetLabel = targetLabel;
            return this;
        }

        public Builder before(Map<String, Object> before) {
            this.before = before;
            return this;
        }

        public Builder after(Map<String, Object> after) {
            this.after = after;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder actor(UUID actorId, String actorUsername, List<String> actorRoles, AuditActorType actorType) {
            this.actorId = actorId;
            this.actorUsername = actorUsername;
            this.actorRoles = actorRoles;
            this.actorType = actorType;
            return this;
        }

        public Builder actorType(AuditActorType actorType) {
            this.actorType = actorType;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(
                    module,
                    action,
                    targetType,
                    targetId,
                    targetLabel,
                    before,
                    after,
                    reason,
                    outcome,
                    errorCode,
                    actorId,
                    actorUsername,
                    actorRoles,
                    actorType);
        }
    }
}
