package vn.unicom.thi360.identity.spi;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import vn.unicom.thi360.shared.security.Access;

public enum Permission {

    USER_VIEW(
            PermissionObject.USER,
            Kind.VIEW,
            "USER.VIEW",
            0,
            Access.MANAGE,
            "Xem tài khoản",
            "Danh sách, chi tiết, lớp của tài khoản, vai trò và quyền hiệu lực của tài khoản"),
    USER_MODIFY(
            PermissionObject.USER,
            Kind.MODIFY,
            "USER.MODIFY",
            1,
            Access.MANAGE,
            "Xem và sửa tài khoản",
            "Tạo, sửa thông tin, cấp hàng loạt, ngừng hoạt động/kích hoạt lại, reset mật khẩu, xóa mềm, thao tác hàng loạt"),
    USER_ASSIGN(
            PermissionObject.USER,
            Kind.EXTRA,
            "USER.ASSIGN",
            2,
            Access.MANAGE,
            "Gán vai trò và quyền",
            "Gán vai trò và quyền (gồm gán hàng loạt)"),
    USER_LEARNING_VIEW(
            PermissionObject.USER,
            Kind.EXTRA,
            "USER.LEARNING_VIEW",
            3,
            Access.MANAGE,
            "Xem dữ liệu học tập",
            "Xem dữ liệu học tập của học viên"),
    CLASS_VIEW(
            PermissionObject.CLASS,
            Kind.VIEW,
            "CLASS.VIEW",
            4,
            Access.MANAGE,
            "Xem lớp học",
            "Danh sách lớp, chi tiết, thành viên"),
    CLASS_MODIFY(
            PermissionObject.CLASS,
            Kind.MODIFY,
            "CLASS.MODIFY",
            5,
            Access.MANAGE,
            "Xem và sửa lớp học",
            "Tạo, sửa, ngừng sử dụng, xóa lớp; thêm, loại, chuyển học viên"),
    ROLE_VIEW(
            PermissionObject.ROLE,
            Kind.VIEW,
            "ROLE.VIEW",
            6,
            Access.MANAGE,
            "Xem vai trò",
            "Tab Vai trò, chi tiết vai trò"),
    ROLE_MODIFY(
            PermissionObject.ROLE,
            Kind.MODIFY,
            "ROLE.MODIFY",
            7,
            Access.MANAGE,
            "Xem và sửa vai trò",
            "Tạo, sửa, xóa vai trò; chọn quyền cho vai trò"),
    PERMISSION_VIEW(
            PermissionObject.PERMISSION,
            Kind.VIEW,
            "PERMISSION.VIEW",
            8,
            Access.MANAGE,
            "Xem quyền",
            "Tab Quyền, tab Người dùng theo quyền (cần thêm USER.VIEW)"),
    CATALOG_VIEW(
            PermissionObject.CATALOG,
            Kind.VIEW,
            "CATALOG.VIEW",
            9,
            Access.MANAGE,
            "Xem danh mục",
            "Màn Cấu hình chung, nơi đang dùng, lịch sử, xuất Excel"),
    CATALOG_MODIFY(
            PermissionObject.CATALOG,
            Kind.MODIFY,
            "CATALOG.MODIFY",
            10,
            Access.MANAGE,
            "Xem và sửa danh mục",
            "Thêm, sửa, sắp xếp, ngừng sử dụng, xóa nhóm và giá trị; mã tổ hợp; nhập Excel"),
    AUDIT_VIEW(
            PermissionObject.AUDIT,
            Kind.VIEW,
            "AUDIT.VIEW",
            11,
            Access.MANAGE,
            "Xem nhật ký",
            "Tra cứu nhật ký thao tác"),
    APPEARANCE_MODIFY(
            PermissionObject.APPEARANCE,
            Kind.MODIFY,
            "APPEARANCE.MODIFY",
            12,
            Access.MANAGE,
            "Sửa giao diện",
            "Đổi chế độ sáng/tối và bộ phối màu toàn hệ thống");

    public enum Kind {
        VIEW,
        MODIFY,
        EXTRA
    }

    private static final Map<String, Permission> BY_CODE;
    private static final Map<Integer, Permission> BY_BIT;
    private static final Map<PermissionObject, Permission> VIEW_BY_OBJECT;

    static {
        Map<String, Permission> byCode = new HashMap<>();
        Map<Integer, Permission> byBit = new HashMap<>();
        Map<PermissionObject, Permission> viewByObj = new EnumMap<>(PermissionObject.class);
        Set<Integer> seenBits = new HashSet<>();

        for (Permission p : values()) {
            if (p.bitIndex < 0) {
                throw new IllegalStateException("Negative bitIndex for " + p.code);
            }
            if (!seenBits.add(p.bitIndex)) {
                throw new IllegalStateException("Duplicate bitIndex " + p.bitIndex);
            }
            if (byCode.put(p.code, p) != null) {
                throw new IllegalStateException("Duplicate permission code " + p.code);
            }
            byBit.put(p.bitIndex, p);
            if (p.kind == Kind.VIEW) {
                if (viewByObj.put(p.object, p) != null) {
                    throw new IllegalStateException("Duplicate VIEW permission for " + p.object);
                }
            }
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
        BY_BIT = Collections.unmodifiableMap(byBit);
        VIEW_BY_OBJECT = Collections.unmodifiableMap(viewByObj);
    }

    private final PermissionObject object;
    private final Kind kind;
    private final String code;
    private final int bitIndex;
    private final Access scope;
    private final String displayName;
    private final String description;

    Permission(
            PermissionObject object,
            Kind kind,
            String code,
            int bitIndex,
            Access scope,
            String displayName,
            String description) {
        this.object = object;
        this.kind = kind;
        this.code = code;
        this.bitIndex = bitIndex;
        this.scope = scope;
        this.displayName = displayName;
        this.description = description;
    }

    public PermissionObject object() {
        return object;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public int bitIndex() {
        return bitIndex;
    }

    public Access scope() {
        return scope;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static Optional<Permission> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim()));
    }

    public static Permission fromCode(String code) {
        return findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission code: " + code));
    }

    public static Optional<Permission> findByBit(int bitIndex) {
        return Optional.ofNullable(BY_BIT.get(bitIndex));
    }

    public static Optional<Permission> viewPermissionOf(PermissionObject object) {
        return Optional.ofNullable(VIEW_BY_OBJECT.get(object));
    }
}
