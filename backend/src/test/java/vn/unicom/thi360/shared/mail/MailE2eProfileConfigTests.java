package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Chỉ profile {@code e2e} (như CI chạy {@code SPRING_PROFILES_ACTIVE=api,worker,e2e}, không có khối
 * {@code test}) phải ra {@code thi360.mail.tls=none} với Mailpit: mặc định của {@link MailProperties} là
 * {@code starttls}, mà Mailpit không có.
 */
class MailE2eProfileConfigTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailProperties.class)
    static class Props {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Props.class);

    @Test
    void e2eProfileAloneUsesPlainSmtp() {
        runner.withPropertyValues("spring.profiles.active=e2e")
                .run(context -> assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(SmtpTls.NONE));
    }

    @Test
    void withoutE2eTheSecureDefaultStays() {
        runner.run(context -> assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(SmtpTls.STARTTLS));
    }
}
