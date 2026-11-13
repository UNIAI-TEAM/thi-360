package vn.unicom.thi360.audit.spi;

public enum AuditActorType {
    USER(1),
    PLATFORM(2),
    SYSTEM(3);

    private final int code;

    AuditActorType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static AuditActorType fromCode(int code) {
        for (AuditActorType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown AuditActorType code: " + code);
    }
}
