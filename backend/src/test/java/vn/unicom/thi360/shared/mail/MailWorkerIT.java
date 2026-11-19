package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Lối vào thật của worker (R1-10) và việc nó không chiếm bộ lập lịch dùng chung (R1-03). Lịch gửi thư bật (khối
 * {@code test} tắt nó ở mọi nơi khác), đồng hồ đứng yên tiêm vào, và cổng SMTP đi qua đúng khóa {@code THI360_SMTP_PORT}
 * ({@code ${THI360_SMTP_PORT:1360}} trong {@code application.yml}) chứ không phải {@code spring.mail.port}. Test không
 * gọi {@link MailDispatcher} trực tiếp. Context đóng sau lớp này để lịch không tiếp tục gửi thư của lớp khác.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({AbstractMailIT.MailTestConfig.class, MailWorkerIT.HeartbeatConfig.class})
@TestPropertySource(
        properties = {
            "thi360.mail.scheduling.enabled=true",
            "thi360.mail.poll-delay=PT0.1S",
            "thi360.mail.attempt-timeout=PT3S",
            "thi360.mail.claim-lease=PT30S",
            "thi360.mail.cleanup-cron=*/1 * * * * *",
            "THI360_SMTP_HOST=127.0.0.1",
            "thi360.mail.tls=none"
        })
class MailWorkerIT extends AbstractIntegrationTest {

    static final AtomicInteger HEARTBEATS = new AtomicInteger();

    @org.springframework.test.context.DynamicPropertySource
    static void smtpPortThroughTheEnvironmentMapping(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("THI360_SMTP_PORT", () -> AbstractMailIT.GREEN_MAIL.getSmtp().getPort());
    }

    /** Một việc {@code @Scheduled} khác trên bộ lập lịch dùng chung, như outbox, audit, tài khoản. */
    @TestConfiguration(proxyBeanMethods = false)
    static class HeartbeatConfig {

        @Bean
        Heartbeat heartbeat() {
            return new Heartbeat();
        }
    }

    public static class Heartbeat {

        @Scheduled(fixedDelay = 25)
        public void beat() {
            HEARTBEATS.incrementAndGet();
        }
    }

    @Autowired
    private MailQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AdjustableClock clock;

    @Autowired
    private JavaMailSenderImpl sender;

    @MockitoSpyBean
    private MailTransport transport;

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
    void theScheduledEntryPointDeliversThroughTheMappedSmtpPort() throws Exception {
        assertThat(sender.getPort()).as("THI360_SMTP_PORT reaches the sender").isEqualTo(AbstractMailIT.GREEN_MAIL.getSmtp().getPort());
        String to = MailSamples.randomAddress();

        enqueue(to);

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> outbox.require(to, MailKind.EMAIL_VERIFY).sentAt() != null);
        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.sentAt()).isEqualTo(AbstractMailIT.START);
        assertThat(mail.attempts()).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> AbstractMailIT.GREEN_MAIL.getReceivedMessages().length == 1);
        assertThat(AbstractMailIT.GREEN_MAIL.getReceivedMessages()[0].getAllRecipients()[0].toString()).isEqualTo(to);
    }

    @Test
    void aStalledSendDoesNotBlockOtherScheduledJobs() throws Exception {
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    inSend.countDown();
                    assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                    return invocation.callRealMethod();
                })
                .when(transport).send(any(), any(), any(), any(), any());
        String to = MailSamples.randomAddress();

        enqueue(to);

        assertThat(inSend.await(15, TimeUnit.SECONDS)).as("the worker is inside the stalled send").isTrue();
        int before = HEARTBEATS.get();
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(HEARTBEATS.get()).as("the shared scheduler keeps running").isGreaterThan(before + 3));
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).as("still stalled").isNull();

        release.countDown();
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> outbox.require(to, MailKind.EMAIL_VERIFY).sentAt() != null);
    }

    @Test
    void aSendThatNeverEndsIsCutAtTheAttemptDeadline() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        doAnswer(invocation -> {
                    never.await(60, TimeUnit.SECONDS); // bị ngắt khi quá hạn
                    return null;
                })
                .when(transport).send(any(), any(), any(), any(), any());
        String to = MailSamples.randomAddress();

        enqueue(to);

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> outbox.require(to, MailKind.EMAIL_VERIFY).attempts() == 1);
        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.lastError()).isEqualTo("timeout");
        assertThat(mail.sentAt()).isNull();
        assertThat(mail.failedAt()).isNull();
        assertThat(mail.nextAttemptAt()).isEqualTo(AbstractMailIT.START.plus(Duration.ofMinutes(1)));
    }

    /**
     * R2-05: lịch dọn thật (cron mỗi giây, đồng hồ đứng yên) xóa thư đã gửi quá hạn giữ, không xóa thư đã
     * gửi gần đây, chưa gửi hay đã thất bại. Test không gọi {@link MailCleanupService} trực tiếp.
     */
    @Test
    void theScheduledCleanupDeletesOnlyExpiredSentMail() throws Exception {
        java.time.Instant now = AbstractMailIT.START;
        java.util.UUID expired = insertMail(now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(31)), null, now.plus(Duration.ofDays(900)));
        java.util.UUID recent = insertMail(now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(29)), null, now.plus(Duration.ofDays(900)));
        java.util.UUID unsent = insertMail(now.minus(Duration.ofDays(60)), null, null, now.plus(Duration.ofDays(900)));
        java.util.UUID failed = insertMail(now.minus(Duration.ofDays(60)), null, now.minus(Duration.ofDays(50)), now.plus(Duration.ofDays(900)));

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                        "SELECT count(*) FROM shared.email_message WHERE id = ?", Integer.class, expired) == 0);

        assertThat(jdbc.queryForList("SELECT id FROM shared.email_message", java.util.UUID.class))
                .containsExactlyInAnyOrder(recent, unsent, failed);
    }

    private java.util.UUID insertMail(java.time.Instant createdAt, java.time.Instant sentAt, java.time.Instant failedAt, java.time.Instant nextAttemptAt) {
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO shared.email_message
                    (id, kind, to_email, locale, subject, html, text, created_at, next_attempt_at, attempts, sent_at, failed_at)
                VALUES (?, 'EMAIL_VERIFY', ?, 'vi', 's', '<p>h</p>', 't', ?, ?, 1, ?, ?)
                """,
                id,
                MailSamples.randomAddress(),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(nextAttemptAt),
                sentAt == null ? null : java.sql.Timestamp.from(sentAt),
                failedAt == null ? null : java.sql.Timestamp.from(failedAt));
        return id;
    }

    private void enqueue(String to) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> queue.enqueue(
                MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null));
    }
}
