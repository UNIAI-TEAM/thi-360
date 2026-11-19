package vn.unicom.thi360.shared.mail;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Truy cập {@code shared.email_message} bằng JDBC thuần. Cố ý không dùng JPA: thân thư chứa mã OTP và
 * liên kết đặt lại mật khẩu, mà Hibernate ghi giá trị tham số ra log khi bật
 * {@code org.hibernate.orm.jdbc.bind} ({@code THI360_LOG_SQL=TRACE}). Ở đây tham số gán bằng
 * {@link java.sql.PreparedStatement} trực tiếp, nên không lớp log nào của ứng dụng thấy giá trị.
 *
 * <p>Mọi ghi kết quả đều chặn theo mã giữ chỗ và trạng thái chưa kết thúc: worker đã mất quyền giữ
 * (hết hạn và bị worker khác lấy lại) không ghi đè được kết quả của worker mới.
 */
@Component
class EmailMessageStore {

    /** Một thư vừa giữ chỗ, mang đủ dữ liệu để gửi mà không cần phiên truy cập dữ liệu. */
    record Claimed(
            UUID id, UUID token, String kind, String to, String subject, String html, String text, int attempts) {
    }

    private static final String INSERT = """
            INSERT INTO shared.email_message
                (id, kind, to_email, locale, subject, html, text, user_id, created_at, next_attempt_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Giữ chỗ thư đến hạn cũ nhất trong một câu lệnh: chọn bằng {@code FOR UPDATE SKIP LOCKED}, đẩy
     * {@code next_attempt_at} ra hạn giữ chỗ và ghi mã giữ chỗ.
     */
    private static final String CLAIM_NEXT = """
            UPDATE shared.email_message
            SET next_attempt_at = ?, claim_token = ?
            WHERE id = (
                SELECT id FROM shared.email_message
                WHERE sent_at IS NULL AND failed_at IS NULL AND next_attempt_at <= ?
                ORDER BY next_attempt_at ASC, created_at ASC, id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED)
            RETURNING id, kind, to_email, subject, html, text, attempts
            """;

    private static final String MARK_SENT = """
            UPDATE shared.email_message
            SET attempts = ?, sent_at = ?, last_error = NULL
            WHERE id = ? AND claim_token = ? AND sent_at IS NULL AND failed_at IS NULL
            """;

    private static final String MARK_RETRY = """
            UPDATE shared.email_message
            SET attempts = ?, next_attempt_at = ?, last_error = ?
            WHERE id = ? AND claim_token = ? AND sent_at IS NULL AND failed_at IS NULL
            """;

    private static final String MARK_FAILED = """
            UPDATE shared.email_message
            SET attempts = ?, failed_at = ?, last_error = ?
            WHERE id = ? AND claim_token = ? AND sent_at IS NULL AND failed_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    EmailMessageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(
            UUID id,
            MailKind kind,
            String to,
            String locale,
            RenderedMail rendered,
            UUID userId,
            Instant createdAt) {
        jdbc.update(INSERT, (PreparedStatement ps) -> {
            ps.setObject(1, id);
            ps.setString(2, kind.name());
            ps.setString(3, to);
            ps.setString(4, locale);
            ps.setString(5, rendered.subject());
            ps.setString(6, rendered.html());
            ps.setString(7, rendered.text());
            if (userId == null) {
                ps.setNull(8, Types.OTHER);
            } else {
                ps.setObject(8, userId);
            }
            ps.setObject(9, utc(createdAt));
            ps.setObject(10, utc(createdAt));
        });
    }

    Optional<Claimed> claimNext(Instant now, Instant heldUntil, UUID token) {
        List<Claimed> rows = jdbc.query(
                CLAIM_NEXT,
                (PreparedStatement ps) -> {
                    ps.setObject(1, utc(heldUntil));
                    ps.setObject(2, token);
                    ps.setObject(3, utc(now));
                },
                (rs, row) -> new Claimed(
                        rs.getObject("id", UUID.class),
                        token,
                        rs.getString("kind"),
                        rs.getString("to_email"),
                        rs.getString("subject"),
                        rs.getString("html"),
                        rs.getString("text"),
                        rs.getInt("attempts")));
        return rows.stream().findFirst();
    }

    /** @return {@code false} khi worker không còn giữ thư (mã giữ chỗ đã đổi) hoặc thư đã kết thúc */
    boolean markSent(Claimed mail, Instant at) {
        return jdbc.update(MARK_SENT, (PreparedStatement ps) -> {
            ps.setInt(1, mail.attempts() + 1);
            ps.setObject(2, utc(at));
            ps.setObject(3, mail.id());
            ps.setObject(4, mail.token());
        }) == 1;
    }

    /** Lỗi tạm: hẹn gửi lại lúc {@code retryAt}. */
    boolean markRetry(Claimed mail, String error, Instant retryAt) {
        return jdbc.update(MARK_RETRY, (PreparedStatement ps) -> {
            ps.setInt(1, mail.attempts() + 1);
            ps.setObject(2, utc(retryAt));
            ps.setString(3, error);
            ps.setObject(4, mail.id());
            ps.setObject(5, mail.token());
        }) == 1;
    }

    /** Thất bại hẳn: không gửi lại. */
    boolean markFailed(Claimed mail, String error, Instant at) {
        return jdbc.update(MARK_FAILED, (PreparedStatement ps) -> {
            ps.setInt(1, mail.attempts() + 1);
            ps.setObject(2, utc(at));
            ps.setString(3, error);
            ps.setObject(4, mail.id());
            ps.setObject(5, mail.token());
        }) == 1;
    }

    int deleteSentBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM shared.email_message WHERE sent_at IS NOT NULL AND sent_at < ?",
                (PreparedStatement ps) -> ps.setObject(1, utc(cutoff)));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
