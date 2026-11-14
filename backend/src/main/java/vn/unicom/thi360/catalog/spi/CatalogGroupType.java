package vn.unicom.thi360.catalog.spi;

public enum CatalogGroupType {
    SYSTEM((short) 1),
    CUSTOM((short) 2);

    private final short code;

    CatalogGroupType(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static CatalogGroupType fromCode(short code) {
        for (CatalogGroupType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown CatalogGroupType code: " + code);
    }
}
