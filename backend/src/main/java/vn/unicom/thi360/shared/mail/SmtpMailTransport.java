package vn.unicom.thi360.shared.mail;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Gửi một thư qua SMTP. Tự mở và đóng kết nối thay cho {@code JavaMailSender.send}, vì lệnh đóng ({@code QUIT})
 * chạy sau khi máy chủ đã nhận thư: nếu nó lỗi hoặc quá hạn thì thư vẫn đã được gửi, không được tính là
 * gửi lỗi (sẽ gửi lại và trùng thư). Việc đóng cũng không được chặn lời gọi {@link #send}: DATA được
 * chấp nhận là kết quả cuối, nên {@code close} chạy tách trên một luồng ảo khác, chỉ để trả socket, và
 * {@code mail.smtp.quitwait=false} để nó không chờ câu trả lời {@code QUIT}.
 *
 * <p>Trường {@code Date} của MIME lấy từ {@link Clock} được tiêm, đồng nhất với {@code sent_at} trong
 * cơ sở dữ liệu.
 */
class SmtpMailTransport implements MailTransport {

    private final JavaMailSenderImpl sender;
    private final String from;
    private final Clock clock;

    SmtpMailTransport(JavaMailSenderImpl sender, String from, Clock clock) {
        this.sender = sender;
        this.from = from;
        this.clock = clock;
        // QUIT không cần câu trả lời: kết quả gửi đã xong khi DATA được nhận; chỉ tôn trọng chỉ định
        // sẵn có của người dùng bộ gửi (mặc định của angus là true).
        this.sender.getJavaMailProperties().putIfAbsent("mail.smtp.quitwait", "false");
    }

    @Override
    public void send(String to, String subject, String html, String text, SendDeadline deadline)
            throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(from, true));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        if (message.getSentDate() == null) {
            message.setSentDate(Date.from(Instant.now(clock)));
        }
        String messageId = message.getMessageID();
        message.saveChanges();
        if (messageId != null) {
            message.setHeader("Message-ID", messageId);
        }
        Address[] recipients = message.getAllRecipients();

        // Thời gian chờ của socket suy ra từ hạn còn lại ngay lúc này, ngay trước khi kết nối.
        Transport transport = sessionFor(deadline).getTransport(protocol());
        try {
            String username = sender.getUsername();
            String password = sender.getPassword();
            if (username != null && username.isEmpty()) {
                username = null;
                if (password != null && password.isEmpty()) {
                    password = null;
                }
            }
            transport.connect(sender.getHost(), sender.getPort(), username, password);
            transport.sendMessage(message, recipients);
        } catch (Exception failure) {
            closeDetached(transport);
            throw failure;
        }
        // Máy chủ đã nhận thư: kết quả cuối. Đóng kết nối tách khỏi lời gọi để một máy chủ chậm
        // trả lời QUIT không làm lần gửi tới hạn, lỡ máy chủ nhận thư rồi.
        closeDetached(transport);
    }

    /**
     * Phiên riêng cho một lần gửi: thời gian chờ kết nối, đọc và ghi bị chặn bởi hạn còn lại của lần gửi
     * (không bao giờ dài hơn cấu hình, không bao giờ vượt hạn giữ chỗ).
     *
     * @throws SendDeadline.ExpiredException khi hạn đã hết: không bắt đầu SMTP
     */
    Session sessionFor(SendDeadline deadline) throws SendDeadline.ExpiredException {
        int remainingMillis = deadline.remainingMillis();
        String prefix = "mail." + protocol() + ".";
        Properties props = new Properties();
        props.putAll(sender.getJavaMailProperties());
        for (String key : new String[] {"connectiontimeout", "timeout", "writetimeout"}) {
            int configured = parseMillis(props.getProperty(prefix + key), remainingMillis);
            props.setProperty(prefix + key, Integer.toString(Math.min(configured, remainingMillis)));
        }
        return Session.getInstance(props);
    }

    private static int parseMillis(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Như {@code JavaMailSenderImpl}: giao thức đặt trên bộ gửi, nếu không thì của phiên, nếu không thì {@code smtp}. */
    private String protocol() {
        String protocol = sender.getProtocol();
        if (protocol == null) {
            protocol = sender.getSession().getProperty("mail.transport.protocol");
        }
        return protocol == null ? JavaMailSenderImpl.DEFAULT_PROTOCOL : protocol;
    }

    /** Đóng kết nối trên luồng ảo khác: chỉ trả tài nguyên, lỗi đóng không liên quan kết quả gửi. */
    private static void closeDetached(Transport transport) {
        Thread.startVirtualThread(() -> {
            try {
                transport.close();
            } catch (MessagingException | RuntimeException ignored) {
                // Có thể là lỗi QUIT sau khi DATA đã được chấp nhận, hoặc kết nối đã hỏng sẵn.
            }
        });
    }
}
