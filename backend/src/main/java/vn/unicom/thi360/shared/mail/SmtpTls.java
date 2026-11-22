package vn.unicom.thi360.shared.mail;

import java.util.Locale;

/** Chế độ mã hóa của {@code THI360_SMTP_TLS}: {@code starttls}, {@code tls}, {@code none}. */
public enum SmtpTls {
    STARTTLS,
    TLS,
    NONE;

    /**
     * Đọc đúng ba cách viết của tài liệu, không phân biệt hoa thường. Không có giá trị (hoặc rỗng) thì dùng
     * {@code starttls}, mặc định an toàn. Cách viết khác ({@code start-tls}, {@code t.l.s}, {@code ssl}, ...)
     * là lỗi cấu hình.
     */
    static SmtpTls parse(String value) {
        if (value == null || value.isBlank()) {
            return STARTTLS;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "starttls" -> STARTTLS;
            case "tls" -> TLS;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException(
                    "thi360.mail.tls must be one of starttls, tls, none (was '" + value + "')");
        };
    }
}
