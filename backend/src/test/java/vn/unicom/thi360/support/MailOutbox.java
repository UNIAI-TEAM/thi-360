package vn.unicom.thi360.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.shared.mail.MailKind;

/**
 * Đọc thư đã xếp hàng thẳng từ {@code shared.email_message} (TM 4: "test đọc thư thẳng từ bảng", như
 * {@code e2e/db.ts} của uniwork). Test của ID-1, ID-2, CM-3a, CM-5, CM-6 dùng lớp này để lấy mã OTP, liên kết,
 * danh sách bài trong thư mà không cần SMTP.
 *
 * <p>Thư mới nhất = {@code created_at} lớn nhất; cùng thời điểm thì thư xếp sau (id UUIDv7 tăng dần).
 * Địa chỉ so khớp không phân biệt hoa thường.
 */
public class MailOutbox {

    public record Mail(
            UUID id,
            MailKind kind,
            String to,
            String locale,
            String subject,
            String html,
            String text,
            UUID userId,
            Instant createdAt,
            Instant nextAttemptAt,
            int attempts,
            Instant sentAt,
            Instant failedAt,
            String lastError) {
    }

    private static final String SELECT = """
            SELECT id, kind, to_email, locale, subject, html, text, user_id, created_at, next_attempt_at,
                   attempts, sent_at, failed_at, last_error
            FROM shared.email_message
            WHERE lower(to_email) = lower(?) AND kind = ?
            ORDER BY created_at DESC, id DESC
            """;

    private final JdbcTemplate jdbc;

    public MailOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Thư mới nhất gửi tới {@code to} thuộc loại {@code kind}. */
    public Optional<Mail> latest(String to, MailKind kind) {
        return all(to, kind).stream().findFirst();
    }

    /** Mọi thư gửi tới {@code to} thuộc loại {@code kind}, mới nhất trước. */
    public List<Mail> all(String to, MailKind kind) {
        return jdbc.query(SELECT, MailOutbox::map, to, kind.name());
    }

    /** Như {@link #latest} nhưng báo lỗi test nếu không có thư. */
    public Mail require(String to, MailKind kind) {
        return latest(to, kind)
                .orElseThrow(() -> new AssertionError("No " + kind + " mail queued for " + to));
    }

    /** Số thư (mọi loại) đã xếp hàng cho {@code to}. */
    public int count(String to) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE lower(to_email) = lower(?)", Integer.class, to);
        return count == null ? 0 : count;
    }

    private static Mail map(ResultSet rs, int row) throws SQLException {
        return new Mail(
                rs.getObject("id", UUID.class),
                MailKind.valueOf(rs.getString("kind")),
                rs.getString("to_email"),
                rs.getString("locale"),
                rs.getString("subject"),
                rs.getString("html"),
                rs.getString("text"),
                rs.getObject("user_id", UUID.class),
                instant(rs, "created_at"),
                instant(rs, "next_attempt_at"),
                rs.getInt("attempts"),
                instant(rs, "sent_at"),
                instant(rs, "failed_at"),
                rs.getString("last_error"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
