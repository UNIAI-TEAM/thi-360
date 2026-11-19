package vn.unicom.thi360.shared.mail;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Gửi một thư qua SMTP. Tự mở và đóng kết nối thay cho {@code JavaMailSender.send}, vì lệnh đóng ({@code QUIT})
 * chạy sau khi máy chủ đã nhận thư: nếu nó lỗi hoặc quá hạn thì thư vẫn đã được gửi, không được tính là
 * gửi lỗi (sẽ gửi lại và trùng thư).
 */
class SmtpMailTransport implements MailTransport {

    private final JavaMailSenderImpl sender;
    private final String from;

    SmtpMailTransport(JavaMailSenderImpl sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html, String text) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(from, true));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        if (message.getSentDate() == null) {
            message.setSentDate(new Date());
        }
        String messageId = message.getMessageID();
        message.saveChanges();
        if (messageId != null) {
            message.setHeader("Message-ID", messageId);
        }
        Address[] recipients = message.getAllRecipients();

        Transport transport = sender.getSession().getTransport(protocol());
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
            closeQuietly(transport);
            throw failure;
        }
        // Máy chủ đã nhận thư. Từ đây mọi lỗi đóng kết nối chỉ là chuyện của kết nối.
        closeQuietly(transport);
    }

    /** Như {@code JavaMailSenderImpl}: giao thức đặt trên bộ gửi, nếu không thì của phiên, nếu không thì {@code smtp}. */
    private String protocol() {
        String protocol = sender.getProtocol();
        if (protocol == null) {
            protocol = sender.getSession().getProperty("mail.transport.protocol");
        }
        return protocol == null ? JavaMailSenderImpl.DEFAULT_PROTOCOL : protocol;
    }

    private static void closeQuietly(Transport transport) {
        try {
            transport.close();
        } catch (MessagingException | RuntimeException ignored) {
            // Có thể là lỗi QUIT sau khi DATA đã được chấp nhận, hoặc kết nối đã hỏng sẵn.
        }
    }
}
