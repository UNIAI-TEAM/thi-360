package vn.unicom.thi360.shared.mail;

import java.util.List;
import java.util.Locale;

/**
 * Các loại thư (TM 4). Mỗi loại khai các khóa model mẫu thư cần; {@link MailQueue#enqueue}
 * từ chối khi thiếu khóa, để lỗi lộ ra lúc xếp hàng thay vì lúc gửi.
 *
 * <p>Kiểu giá trị: mọi khóa là chuỗi, trừ {@link #ORDER_FULFILLED}. Mẫu in chuỗi nguyên văn và thoát HTML nên bên gọi
 * truyền sẵn dạng hiển thị, ví dụ {@code refundAmount} của {@link #REFUND_DONE} đã định dạng theo ngôn ngữ thư.
 * {@code resetUrl} là liên kết đầy đủ do bên gọi dựng. Với {@link #ORDER_FULFILLED}, {@code items} là danh sách các
 * {@code Map} có khóa {@code name} (tên bài được cấp, TM 16.4); một đơn bài lẻ truyền danh sách một phần tử.
 */
public enum MailKind {

    EMAIL_VERIFY(List.of("fullName", "code")),
    EMAIL_CHANGE(List.of("fullName", "code", "newEmail")),
    EMAIL_CHANGED_NOTICE(List.of("fullName", "newEmail")),
    PASSWORD_RESET(List.of("fullName", "resetUrl")),
    ORDER_FULFILLED(List.of("fullName", "orderCode", "items")),
    REFUND_DONE(List.of("fullName", "orderCode", "refundAmount"));

    private final List<String> requiredModelKeys;

    MailKind(List<String> requiredModelKeys) {
        this.requiredModelKeys = requiredModelKeys;
    }

    public List<String> requiredModelKeys() {
        return requiredModelKeys;
    }

    /** Tiền tố tên tệp mẫu: {@code <templateName>.<vi|en>.<html|txt>}. */
    public String templateName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
