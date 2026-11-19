package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.MailOutbox;

/**
 * R2-06: {@code thi360.mail.cleanup-cron: "-"} (dấu tắt của Spring) chỉ tắt việc dọn; lịch gửi thư vẫn
 * chạy và worker vẫn khởi động. Thư đã gửi quá hạn giữ vẫn còn nguyên dù lịch gửi đã chạy nhiều lượt.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AbstractMailIT.MailTestConfig.class)
@TestPropertySource(
        properties = {
            "thi360.mail.scheduling.enabled=true",
            "thi360.mail.poll-delay=PT0.1S",
            "thi360.mail.cleanup-cron=-",
            "THI360_SMTP_HOST=127.0.0.1",
            "thi360.mail.tls=none"
        })
class MailCleanupDisabledIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void smtpPort(DynamicPropertyRegistry registry) {
        registry.add("THI360_SMTP_PORT", () -> AbstractMailIT.GREEN_MAIL.getSmtp().getPort());
    }

    @Autowired
    private MailQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AdjustableClock clock;

    private MailOutbox outbox;

    @BeforeEach
    void reset() throws Exception {
        outbox = new MailOutbox(jdbc);
        clock.set(AbstractMailIT.START);
        jdbc.update("DELETE FROM shared.email_message");
        AbstractMailIT.GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    @Test
    void disablingCleanupKeepsDeliveryRunning() {
        Instant now = AbstractMailIT.START;
        UUID expired = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO shared.email_message
                    (id, kind, to_email, locale, subject, html, text, created_at, next_attempt_at, attempts, sent_at)
                VALUES (?, 'EMAIL_VERIFY', ?, 'vi', 's', '<p>h</p>', 't', ?, ?, 1, ?)
                """,
                expired,
                MailSamples.randomAddress(),
                Timestamp.from(now.minus(Duration.ofDays(90))),
                Timestamp.from(now.plus(Duration.ofDays(900))),
                Timestamp.from(now.minus(Duration.ofDays(60))));
        String to = MailSamples.randomAddress();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> queue.enqueue(
                MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null));

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> outbox.require(to, MailKind.EMAIL_VERIFY).sentAt() != null);
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).isEqualTo(now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.email_message WHERE id = ?", Integer.class, expired))
                .as("cleanup is disabled: the expired sent mail is still there")
                .isEqualTo(1);
    }
}
