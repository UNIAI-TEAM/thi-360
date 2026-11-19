package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vn.unicom.thi360.support.MailOutbox;

/**
 * R1-04 (AC-20): với {@code THI360_LOG_SQL=TRACE} (bật log tham số của Hibernate) và cả log tham số của Spring JDBC,
 * xếp hàng, lấy và gửi thư chứa mã OTP, liên kết đặt lại mật khẩu không để lọt thân thư hay mã vào log.
 */
class MailLogRedactionIT extends AbstractMailIT {

    private static final List<String> TRACED_LOGGERS = List.of(
            "org.hibernate.SQL",
            "org.hibernate.orm.jdbc.bind",
            "org.hibernate.orm.jdbc.extract",
            "org.springframework.jdbc.core",
            "org.springframework.jdbc.core.StatementCreatorUtils",
            "org.springframework.jdbc.core.JdbcTemplate");

    private final Map<Logger, Level> previousLevels = new java.util.LinkedHashMap<>();
    private ListAppender<ILoggingEvent> captured;
    private Logger root;

    @BeforeEach
    void traceEveryParameterLog() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        captured = new ListAppender<>();
        captured.setContext(context);
        captured.start();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(captured);
        for (String name : TRACED_LOGGERS) {
            Logger logger = context.getLogger(name);
            previousLevels.put(logger, logger.getLevel());
            logger.setLevel(Level.TRACE);
        }
    }

    @AfterEach
    void restoreLogging() {
        previousLevels.forEach(Logger::setLevel);
        root.detachAppender(captured);
        captured.stop();
    }

    @Test
    void enqueueClaimAndSendLeakNeitherBodyNorCredentialsToTheLogs() {
        // Đối chứng: một câu lệnh JDBC có tham số thường đi qua đúng bộ ghi log này và để lộ giá trị. Nếu dòng này
        // không lộ thì phần còn lại của test không chứng minh được gì.
        String probe = "probe-" + MailSamples.randomCode() + "-" + MailSamples.randomCode();
        jdbc.queryForObject("SELECT ?", String.class, probe);
        assertThat(logged()).as("the test really captures JDBC parameter logs").contains(probe);

        String otp = "9" + MailSamples.randomCode();
        String resetToken = "tok-" + MailSamples.randomCode() + MailSamples.randomCode();
        String resetUrl = "https://thi360.example.test/reset-password?token=" + resetToken;
        String verifyTo = MailSamples.randomAddress();
        String resetTo = MailSamples.randomAddress();
        // Chỉ xét log do các bước của ứng dụng sinh ra: xếp hàng, rồi lấy và gửi. Đọc lại bảng bằng MailOutbox (đoạn
        // trong test) cũng là JdbcTemplate có tham số nên tự nó ghi log, và GreenMail (máy chủ giả) ghi địa chỉ nhận.
        captured.list.clear();
        tx.executeWithoutResult(status -> {
            queue.enqueue(MailKind.EMAIL_VERIFY, verifyTo, "vi", MailSamples.model(MailKind.EMAIL_VERIFY, otp), null);
            Map<String, Object> reset = new java.util.HashMap<>(MailSamples.model(MailKind.PASSWORD_RESET));
            reset.put("resetUrl", resetUrl);
            queue.enqueue(MailKind.PASSWORD_RESET, resetTo, "en", reset, null);
        });
        String enqueueLogs = logged();
        MailOutbox.Mail verify = outbox.require(verifyTo, MailKind.EMAIL_VERIFY);
        MailOutbox.Mail resetMail = outbox.require(resetTo, MailKind.PASSWORD_RESET);

        captured.list.clear();
        assertThat(dispatcher.dispatchBatch()).isEqualTo(2);
        String dispatchLogs = logged();
        assertThat(enqueueLogs).as("the enqueue statements were logged (without values)").containsIgnoringCase("INSERT INTO shared.email_message");

        // Thư vẫn mang đủ nội dung.
        assertThat(verify.html()).contains(otp);
        assertThat(verify.text()).contains(otp);
        assertThat(resetMail.html()).contains(resetToken);
        assertThat(resetMail.text()).contains(resetToken);
        // Log không có mã, thân thư, tiêu đề hay người nhận.
        String logs = enqueueLogs + dispatchLogs;
        List<String> secrets = new ArrayList<>(List.of(otp, resetToken, verify.subject(), resetMail.subject(), verifyTo, resetTo));
        secrets.add(verify.text().lines().filter(line -> line.contains(otp)).findFirst().orElseThrow().strip());
        secrets.add(resetMail.html().substring(0, 200));
        for (String secret : secrets) {
            assertThat(logs).as("logs must not contain '%s'", secret.length() > 24 ? secret.substring(0, 24) + "..." : secret)
                    .doesNotContain(secret);
        }
    }

    private String logged() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(captured.list)) {
            if (!event.getLoggerName().startsWith("com.icegreen.greenmail")) {
                all.append(event.getFormattedMessage()).append('\n');
            }
        }
        return all.toString();
    }
}
