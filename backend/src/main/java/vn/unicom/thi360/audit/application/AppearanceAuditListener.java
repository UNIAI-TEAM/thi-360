package vn.unicom.thi360.audit.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.appearance.application.AppearanceAuditRecorder;

@Component
public class AppearanceAuditListener implements AppearanceAuditRecorder {

    private static final String MODULE = "shared";
    private static final String ACTION_APPEARANCE_UPDATED = "APPEARANCE_UPDATED";
    private static final String TARGET_TYPE = "SYSTEM_SETTING";
    private static final String TARGET_ID = "appearance";
    private static final String TARGET_LABEL = "appearance";

    private final AuditRecorder auditRecorder;

    public AppearanceAuditListener(AuditRecorder auditRecorder) {
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    }

    @Override
    public void recordUpdated(
            UUID actorId,
            String actorUsername,
            boolean platformAdmin,
            Map<String, Object> before,
            Map<String, Object> after) {
        AuditActorType actorType = platformAdmin ? AuditActorType.PLATFORM : AuditActorType.USER;
        auditRecorder.record(AuditEvent.builder(MODULE, ACTION_APPEARANCE_UPDATED)
                .target(TARGET_TYPE, TARGET_ID, TARGET_LABEL)
                .before(before)
                .after(after)
                .actor(actorId, actorUsername, null, actorType)
                .build());
    }
}
