package vn.unicom.thi360.identity.domain;

public enum UserSource {
    ADMIN_CREATED((short) 1),
    BULK_IMPORT((short) 2),
    SELF_REGISTERED((short) 3),
    GOOGLE((short) 4);

    private final short code;

    UserSource(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static UserSource fromCode(short code) {
        for (UserSource value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown UserSource code: " + code);
    }
}
