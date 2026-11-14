package vn.unicom.thi360.catalog.spi;

public enum CatalogStatus {
    ACTIVE((short) 1),
    INACTIVE((short) 2);

    private final short code;

    CatalogStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static CatalogStatus fromCode(short code) {
        for (CatalogStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown CatalogStatus code: " + code);
    }
}
