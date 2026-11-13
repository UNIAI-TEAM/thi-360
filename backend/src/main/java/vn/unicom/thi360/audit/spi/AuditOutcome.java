package vn.unicom.thi360.audit.spi;

public enum AuditOutcome {
    SUCCESS(1),
    FAILURE(2);

    private final int code;

    AuditOutcome(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static AuditOutcome fromCode(int code) {
        for (AuditOutcome value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown AuditOutcome code: " + code);
    }
}
