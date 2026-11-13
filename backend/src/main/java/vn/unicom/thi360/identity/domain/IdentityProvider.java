package vn.unicom.thi360.identity.domain;

public enum IdentityProvider {
    LOCAL((short) 1),
    GOOGLE((short) 2);

    private final short code;

    IdentityProvider(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static IdentityProvider fromCode(short code) {
        for (IdentityProvider value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown IdentityProvider code: " + code);
    }
}
