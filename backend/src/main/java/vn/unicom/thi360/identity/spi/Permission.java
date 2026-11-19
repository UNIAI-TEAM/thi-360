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
            "Đổi chế độ sáng/tối và bộ phối màu toàn hệ thống"),
    QUESTION_VIEW(
            PermissionObject.QUESTION,
            Kind.VIEW,
            "QUESTION.VIEW",
            13,
            Access.MANAGE,
            "Xem ngân hàng câu hỏi",
            "Danh sách, chi tiết câu hỏi, nơi đang dùng, lịch sử phiên bản, lịch sử nhập"),
    QUESTION_MODIFY(
            PermissionObject.QUESTION,
            Kind.MODIFY,
            "QUESTION.MODIFY",
            14,
            Access.MANAGE,
            "Xem và sửa ngân hàng câu hỏi",
            "Tạo, sửa, sao chép, đánh dấu Sẵn sàng, ngừng sử dụng, xóa câu hỏi; nhập từ tệp"),
    EXAM_VIEW(
            PermissionObject.EXAM,
            Kind.VIEW,
            "EXAM.VIEW",
            15,
            Access.MANAGE,
            "Xem bài thi",
            "Danh sách, chi tiết bài thi, cấu trúc, kiểm tra cấu hình, xem trước, phiên bản"),
    EXAM_MODIFY(
            PermissionObject.EXAM,
            Kind.MODIFY,
            "EXAM.MODIFY",
            16,
            Access.MANAGE,
            "Xem và sửa bài thi",
            "Tạo, sửa thông tin và cấu trúc, nạp mẫu, sao chép bài thi"),
    EXAM_PUBLISH(
            PermissionObject.EXAM,
            Kind.EXTRA,
            "EXAM.PUBLISH",
            17,
            Access.MANAGE,
            "Công bố bài thi",
            "Công bố phiên bản mới và ngừng bán bài thi"),
    PROMOTION_VIEW(
            PermissionObject.PROMOTION,
            Kind.VIEW,
            "PROMOTION.VIEW",
            18,
            Access.MANAGE,
            "Xem khuyến mãi",
            "Danh sách, chi tiết khuyến mãi và voucher, lịch sử thay đổi"),
    PROMOTION_MODIFY(
            PermissionObject.PROMOTION,
            Kind.MODIFY,
            "PROMOTION.MODIFY",
            19,
            Access.MANAGE,
            "Xem và sửa khuyến mãi",
            "Tạo, sửa, tạm dừng khuyến mãi và voucher"),
    ORDER_VIEW(
            PermissionObject.ORDER,
            Kind.VIEW,
            "ORDER.VIEW",
            20,
            Access.MANAGE,
            "Xem đơn hàng",
            "Danh sách, chi tiết đơn hàng, người mua theo bài thi, báo cáo đối soát"),
    ORDER_REFUND(
            PermissionObject.ORDER,
            Kind.EXTRA,
            "ORDER.REFUND",
            21,
            Access.MANAGE,
            "Hoàn tiền đơn hàng",
            "Hoàn tiền một phần hoặc toàn bộ đơn hàng, hoàn khoản thu thừa"),
    ORDER_RECOVER(
            PermissionObject.ORDER,
            Kind.EXTRA,
            "ORDER.RECOVER",
            22,
            Access.MANAGE,
            "Xử lý đơn kẹt",
            "Tiếp tục cấp quyền cho đơn đã thu tiền, xử lý tiền về muộn"),
    GRADING_VIEW(
            PermissionObject.GRADING,
            Kind.VIEW,
            "GRADING.VIEW",
            23,
            Access.MANAGE,
            "Xem hàng chờ chấm",
            "Hàng chờ chấm tự luận, số đếm hàng chờ, chi tiết câu cần chấm"),
    GRADING_MODIFY(
            PermissionObject.GRADING,
            Kind.MODIFY,
            "GRADING.MODIFY",
            24,
            Access.MANAGE,
            "Xem và chấm bài",
            "Nhận chấm, lưu nháp, chốt điểm, mở lại chấm, chạy lại chấm lượt lỗi"),
    ATTEMPT_VIEW(
            PermissionObject.ATTEMPT,
            Kind.VIEW,
            "ATTEMPT.VIEW",
            25,
            Access.MANAGE,
            "Xem lượt thi",
            "Danh sách, chi tiết lượt thi, dòng sự kiện, câu trả lời đã nhận"),
    ATTEMPT_COMPENSATE(
            PermissionObject.ATTEMPT,
            Kind.EXTRA,
            "ATTEMPT.COMPENSATE",
            26,
            Access.MANAGE,
            "Đánh dấu hỏng và cấp bù",
            "Đánh dấu lượt thi hỏng vì lỗi hệ thống và cấp một lượt bù");

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
