package vn.unicom.thi360.identity.domain;

public enum RoleStatus {
    ACTIVE((short) 1),
    INACTIVE((short) 2);

    private final short code;

    RoleStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static RoleStatus fromCode(short code) {
        for (RoleStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown RoleStatus code: " + code);
    }
}
