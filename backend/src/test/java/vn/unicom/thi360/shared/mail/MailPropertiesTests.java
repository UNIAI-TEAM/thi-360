package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfiguration.class)
            .withPropertyValues(
                    "thi360.web-url=https://thi360.example.test", "spring.mail.host=smtp.example.test", "spring.mail.port=2525");

    @ParameterizedTest(name = "THI360_SMTP_TLS={0} is accepted as {1}")
    @CsvSource({"starttls,STARTTLS", "tls,TLS", "none,NONE", "STARTTLS,STARTTLS", "None,NONE"})
    void acceptsStarttlsTlsAndNone(String value, SmtpTls expected) {
        runner.withPropertyValues("thi360.mail.tls=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(expected);
        });
    }

    @ParameterizedTest(name = "THI360_SMTP_TLS={0} stops the application")
    @CsvSource({"ssl", "true", "starttls-required", "off"})
    void otherValuesStopStartup(String value) {
        runner.withPropertyValues("thi360.mail.tls=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(hasCause(context.getStartupFailure(), BindException.class)).isTrue();
        });
    }

    @Test
    void emptyValueMeansNotSetAndKeepsTheSecureDefault() {
        // THI360_SMTP_TLS= (present but empty) is no value at all: Spring Boot binds it as unset, so starttls applies.
        runner.withPropertyValues("thi360.mail.tls=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(SmtpTls.STARTTLS);
        });
    }

    @Test
    void defaultIsStarttls() {
        runner.run(context -> assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(SmtpTls.STARTTLS));
    }

    @Test
    void modeIsAppliedToTheJavaMailSession() {
        runner.withPropertyValues("thi360.mail.tls=starttls").run(context -> {
            Properties props = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props).containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .doesNotContainKey("mail.smtp.ssl.enable");
        });
        runner.withPropertyValues("thi360.mail.tls=tls").run(context -> {
            Properties props = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props).containsEntry("mail.smtp.ssl.enable", "true").doesNotContainKey("mail.smtp.starttls.enable");
        });
        runner.withPropertyValues("thi360.mail.tls=none").run(context -> {
            Properties props = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props).doesNotContainKey("mail.smtp.starttls.enable").doesNotContainKey("mail.smtp.ssl.enable");
        });
    }

    @Test
    void hostPortAndTimeoutsComeFromConfiguration() {
        runner.withPropertyValues("thi360.mail.tls=none", "thi360.mail.connect-timeout=PT3S", "thi360.mail.read-timeout=PT7S")
                .run(context -> {
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.example.test");
                    assertThat(sender.getPort()).isEqualTo(2525);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "3000")
                            .containsEntry("mail.smtp.timeout", "7000")
                            .containsEntry("mail.smtp.writetimeout", "7000");
                });
    }

    @Test
    void authenticationIsOnlyUsedWhenAUsernameIsGiven() {
        runner.withPropertyValues("thi360.mail.tls=none").run(context -> {
            JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getUsername()).isNull();
            assertThat(sender.getJavaMailProperties()).doesNotContainKey("mail.smtp.auth");
        });
        runner.withPropertyValues("thi360.mail.tls=none", "spring.mail.username=mailer").run(context -> {
            JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getUsername()).isEqualTo("mailer");
            assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true");
        });
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
