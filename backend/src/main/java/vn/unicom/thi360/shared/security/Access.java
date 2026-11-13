package vn.unicom.thi360.shared.security;

public enum Access {
    MANAGE((short) 1),
    EXAM((short) 2);

    private final short code;

    Access(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static Access fromCode(short code) {
        for (Access value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Access code: " + code);
    }
}
