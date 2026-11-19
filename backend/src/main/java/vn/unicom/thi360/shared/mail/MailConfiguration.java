package vn.unicom.thi360.shared.mail;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * Nối hạ tầng gửi thư. {@code JavaMailSender} dựng ở đây thay cho tự cấu hình của Spring Boot, vì chế độ
 * mã hóa lấy từ một biến ({@code THI360_SMTP_TLS}) và phải kiểm giá trị lúc khởi động.
 */
@Configuration
@EnableConfigurationProperties({MailProperties.class, org.springframework.boot.autoconfigure.mail.MailProperties.class})
class MailConfiguration {

    @Bean
    MailTemplateRenderer mailTemplateRenderer(
            ApplicationContext applicationContext, MailProperties mail, @Value("${thi360.web-url}") String webUrl) {
        return new MailTemplateRenderer(applicationContext, mail.templatesLocation(), webUrl);
    }

    @Bean
    MailTemplateValidator mailTemplateValidator(ApplicationContext applicationContext, MailProperties mail) {
        return new MailTemplateValidator(applicationContext, mail.templatesLocation());
    }

    @Bean
    JavaMailSenderImpl javaMailSender(
            org.springframework.boot.autoconfigure.mail.MailProperties smtp, MailProperties mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        if (smtp.getPort() != null) {
            sender.setPort(smtp.getPort());
        }
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", Long.toString(mail.connectTimeout().toMillis()));
        props.put("mail.smtp.timeout", Long.toString(mail.readTimeout().toMillis()));
        props.put("mail.smtp.writetimeout", Long.toString(mail.readTimeout().toMillis()));
        if (StringUtils.hasText(smtp.getUsername())) {
            sender.setUsername(smtp.getUsername());
            sender.setPassword(smtp.getPassword());
            props.put("mail.smtp.auth", "true");
        }
        switch (mail.tls()) {
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case TLS -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
            }
        }
        return sender;
    }

    @Bean
    MailTransport mailTransport(JavaMailSenderImpl sender, MailProperties mail) {
        return new SmtpMailTransport(sender, mail.from());
    }
}
