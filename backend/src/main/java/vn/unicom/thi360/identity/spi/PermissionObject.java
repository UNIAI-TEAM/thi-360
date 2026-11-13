package vn.unicom.thi360.identity.spi;

public enum PermissionObject {
    USER("USER", "Tài khoản"),
    CLASS("CLASS", "Lớp học"),
    ROLE("ROLE", "Vai trò"),
    PERMISSION("PERMISSION", "Quyền"),
    CATALOG("CATALOG", "Danh mục"),
    AUDIT("AUDIT", "Nhật ký"),
    APPEARANCE("APPEARANCE", "Giao diện");

    private final String code;
    private final String displayName;

    PermissionObject(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}
