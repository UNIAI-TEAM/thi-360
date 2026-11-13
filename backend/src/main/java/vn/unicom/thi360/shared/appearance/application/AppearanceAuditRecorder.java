package vn.unicom.thi360.shared.appearance.application;

import java.util.Map;
import java.util.UUID;

public interface AppearanceAuditRecorder {

    void recordUpdated(
            UUID actorId,
            String actorUsername,
            boolean platformAdmin,
            Map<String, Object> before,
            Map<String, Object> after);
}
