package vn.unicom.thi360.identity.domain;

public enum ImportRowStatus {
    VALID((short) 1),
    INVALID((short) 2),
    CREATED((short) 3),
    FAILED((short) 4);

    private final short code;

    ImportRowStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static ImportRowStatus fromCode(short code) {
        for (ImportRowStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ImportRowStatus code: " + code);
    }
}
