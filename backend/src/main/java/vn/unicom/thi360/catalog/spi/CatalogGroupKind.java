package vn.unicom.thi360.catalog.spi;

public enum CatalogGroupKind {
    SIMPLE((short) 1),
    COMBINATION((short) 2),
    TREE((short) 3);

    private final short code;

    CatalogGroupKind(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static CatalogGroupKind fromCode(short code) {
        for (CatalogGroupKind value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown CatalogGroupKind code: " + code);
    }
}
