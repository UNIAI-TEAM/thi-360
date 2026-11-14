package vn.unicom.thi360.classroom.domain;

public enum ClassStatus {
    ACTIVE((short) 1),
    INACTIVE((short) 2);

    private final short code;

    ClassStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static ClassStatus fromCode(short code) {
        for (ClassStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ClassStatus code: " + code);
    }
}
