package vn.unicom.thi360.audit.spi;

public interface AuditRecorder {

    void record(AuditEvent event);
}
