package vn.unicom.thi360.identity.domain;

public enum UserStatus {
    PROVISIONING((short) 1),
    ACTIVE((short) 2),
    INACTIVE((short) 3);

    private final short code;

    UserStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static UserStatus fromCode(short code) {
        for (UserStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus code: " + code);
    }
}
