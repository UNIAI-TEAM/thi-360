package vn.unicom.thi360.shared.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Nền cho test tích hợp của hàng thư: Postgres thật (Testcontainers), GreenMail ở cổng ngẫu nhiên làm máy chủ
 * SMTP, đồng hồ đứng yên tiêm vào {@code Clock}, lịch worker tắt (khối {@code test}) nên test tự gọi
 * {@link MailDispatcher#dispatchBatch()}. {@link MailTransport} là spy: mặc định gửi thật tới GreenMail,
 * test chuyển sang {@link ScriptedSmtpServer} hoặc quan sát lời gọi khi cần.
 */
@Import(AbstractMailIT.MailTestConfig.class)
abstract class AbstractMailIT extends AbstractIntegrationTest {

    static final Instant START = Instant.parse("2027-01-01T00:00:00Z");

    static final GreenMail GREEN_MAIL = new GreenMail(ServerSetup.SMTP.dynamicPort());

    static {
        GREEN_MAIL.start();
        Runtime.getRuntime().addShutdownHook(new Thread(GREEN_MAIL::stop));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailTestConfig {

        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock(START);
        }
    }

    @DynamicPropertySource
    static void smtpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("thi360.mail.tls", () -> "none");
    }

    @Autowired
    protected MailQueue queue;

    @Autowired
    protected MailDispatcher dispatcher;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired
    protected AdjustableClock clock;

    @MockitoSpyBean
    protected MailTransport transport;

    protected MailOutbox outbox;
    protected TransactionTemplate tx;

    @BeforeEach
    void resetMailFixtures() throws Exception {
        outbox = new MailOutbox(jdbc);
        tx = new TransactionTemplate(transactionManager);
        clock.set(START);
        // Các ca ở đây đếm lô và lượt lấy thư nên phải thấy đúng thư của mình, không thấy thư của lớp test khác.
        jdbc.update("DELETE FROM shared.email_message");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    /** Xếp một thư với model mẫu trong một transaction riêng. */
    protected UUID enqueue(MailKind kind, String locale, String to) {
        return tx.execute(status -> queue.enqueue(kind, to, locale, MailSamples.model(kind), null));
    }

    /** Chuyển việc gửi sang máy chủ SMTP kịch bản ở {@code port} (thay vì GreenMail). */
    protected void routeSmtpTo(int port) throws Exception {
        routeSmtpTo(port, 5000);
    }

    /** Như {@link #routeSmtpTo(int)} với thời gian chờ đọc {@code readTimeoutMillis}. */
    protected void routeSmtpTo(int port, int readTimeoutMillis) throws Exception {
        routeSmtpTo(port, readTimeoutMillis, null, null);
    }

    /** Như {@link #routeSmtpTo(int, int)} còn gửi kèm tài khoản SMTP. */
    protected void routeSmtpTo(int port, int readTimeoutMillis, String username, String password) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        if (username != null) {
            sender.setUsername(username);
            sender.setPassword(password);
            sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        }
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", Integer.toString(readTimeoutMillis));
        SmtpMailTransport scripted = new SmtpMailTransport(sender, "Thi 360 <no-reply@thi360.local>", clock);
        doAnswer(invocation -> {
                    scripted.send(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3));
                    return null;
                })
                .when(transport)
                .send(any(), any(), any(), any());
    }

    /** Một cổng không có ai lắng nghe: kết nối bị từ chối. */
    protected static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    protected void advance(Duration duration) {
        clock.advance(duration);
    }

    // --- đọc thư đã tới GreenMail ---

    record Received(String to, String subject, String text, String html) {
    }

    /** Đợi GreenMail có ít nhất {@code count} thư rồi trả về toàn bộ. */
    protected static Received[] receivedAtLeast(int count) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> GREEN_MAIL.getReceivedMessages().length >= count);
        return Arrays.stream(GREEN_MAIL.getReceivedMessages()).map(AbstractMailIT::parse).toArray(Received[]::new);
    }

    protected static int receivedCount() {
        return GREEN_MAIL.getReceivedMessages().length;
    }

    private static Received parse(MimeMessage message) {
        try {
            String[] parts = new String[2];
            collect(message, parts);
            String to = ((InternetAddress) message.getAllRecipients()[0]).getAddress();
            return new Received(to, message.getSubject(), parts[0], parts[1]);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read received mail", ex);
        }
    }

    private static void collect(Part part, String[] parts) throws Exception {
        if (part.isMimeType("text/plain")) {
            parts[0] = (String) part.getContent();
        } else if (part.isMimeType("text/html")) {
            parts[1] = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                collect(body, parts);
            }
        }
    }
}
