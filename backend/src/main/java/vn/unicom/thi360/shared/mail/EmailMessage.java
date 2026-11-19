package vn.unicom.thi360.shared.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Một thư đã dựng sẵn (tiêu đề, HTML, văn bản) chờ worker gửi (TM 4). Thân thư có thể chứa mã OTP:
 * không đưa vào log và không đưa vào {@code last_error}.
 */
@Entity
@Table(name = "email_message", schema = "shared")
public class EmailMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, updatable = false)
    private String kind;

    @Column(name = "to_email", nullable = false, updatable = false)
    private String toEmail;

    @Column(name = "locale", nullable = false, updatable = false)
    private String locale;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "html", nullable = false, updatable = false)
    private String html;

    @Column(name = "text", nullable = false, updatable = false)
    private String text;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error")
    private String lastError;

    protected EmailMessage() {
    }

    public EmailMessage(
            UUID id,
            MailKind kind,
            String toEmail,
            String locale,
            RenderedMail rendered,
            UUID userId,
            Instant createdAt) {
        this.id = id;
        this.kind = kind.name();
        this.toEmail = toEmail;
        this.locale = locale;
        this.subject = rendered.subject();
        this.html = rendered.html();
        this.text = rendered.text();
        this.userId = userId;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getToEmail() {
        return toEmail;
    }

    public String getLocale() {
        return locale;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtml() {
        return html;
    }

    public String getText() {
        return text;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getLastError() {
        return lastError;
    }

    /** Giữ chỗ: đẩy {@code next_attempt_at} ra xa để worker khác không lấy lại trong lúc gửi SMTP. */
    void claim(Instant heldUntil) {
        this.nextAttemptAt = heldUntil;
    }

    void markSent(Instant at) {
        this.attempts++;
        this.sentAt = at;
        this.lastError = null;
    }

    /** Lỗi có thể gửi lại: lần lỗi thứ 5 thì thất bại, còn lại hẹn theo {@link MailRetrySchedule}. */
    void recordFailure(String error, Instant at) {
        this.attempts++;
        this.lastError = error;
        if (MailRetrySchedule.hasRetryLeft(attempts)) {
            this.nextAttemptAt = at.plus(MailRetrySchedule.delayAfter(attempts));
        } else {
            this.failedAt = at;
        }
    }

    /** Lỗi vĩnh viễn (5xx ở {@code RCPT TO}): thất bại ngay. */
    void markFailedPermanently(String error, Instant at) {
        this.attempts++;
        this.lastError = error;
        this.failedAt = at;
    }
}
