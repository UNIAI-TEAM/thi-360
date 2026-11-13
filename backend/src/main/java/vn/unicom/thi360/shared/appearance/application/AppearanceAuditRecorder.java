package vn.unicom.thi360.shared.appearance.application;

import java.util.Map;

public interface AppearanceAuditRecorder {

    void recordUpdated(Map<String, Object> before, Map<String, Object> after);
}
