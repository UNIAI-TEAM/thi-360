package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfiguration.class)
            .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
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
    @CsvSource({"ssl", "true", "starttls-required", "off", "start-tls", "t.l.s", "start_tls", "TLS1.2", "no-ne"})
    void otherValuesStopStartup(String value) {
        runner.withPropertyValues("thi360.mail.tls=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(hasCause(context.getStartupFailure(), BindException.class)).isTrue();
            assertThat(rootMessage(context.getStartupFailure())).contains("thi360.mail.tls");
        });
    }

    @ParameterizedTest(name = "THI360_SMTP_TLS=\"{0}\" (blank) falls back to starttls")
    @ValueSource(strings = {"", " ", "   "})
    void blankValueKeepsTheSecureDefault(String value) {
        // THI360_SMTP_TLS= (set but blank), as docker compose passes an unset variable of the .env file.
        runner.withPropertyValues("thi360.mail.tls=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).tls()).isEqualTo(SmtpTls.STARTTLS);
        });
    }

    @ParameterizedTest(name = "batch-size={0} stops the application")
    @ValueSource(strings = {"0", "-1", "21", "1000"})
    void batchSizeOutsideOneToTwentyStopsStartup(String value) {
        runner.withPropertyValues("thi360.mail.batch-size=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("thi360.mail.batch-size");
        });
    }

    @ParameterizedTest(name = "batch-size={0} is accepted")
    @ValueSource(strings = {"1", "20"})
    void batchSizeBoundsAreAccepted(String value) {
        runner.withPropertyValues("thi360.mail.batch-size=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MailProperties.class).batchSize()).isEqualTo(Integer.parseInt(value));
        });
    }

    @ParameterizedTest(name = "claim-lease={0} stops the application")
    @ValueSource(strings = {"PT0S", "-PT1S", "PT2M", "PT1M", "PT2M4.999S"})
    void claimLeaseMustBePositiveAndLongerThanTheAttemptDeadlinePlusTheSafetyMargin(String value) {
        runner.withPropertyValues("thi360.mail.claim-lease=" + value, "thi360.mail.attempt-timeout=PT2M").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("thi360.mail.claim-lease");
        });
    }

    /** R3-01: at the boundary, a lease of exactly attempt-timeout + 5 s starts and a fresh claim can start SMTP. */
    @Test
    void theSmallestClaimLeaseStillLeavesAFreshClaimTheWholeAttemptBudget() {
        runner.withPropertyValues("thi360.mail.claim-lease=PT2M5S", "thi360.mail.attempt-timeout=PT2M").run(context -> {
            assertThat(context).hasNotFailed();
            MailProperties mail = context.getBean(MailProperties.class);
            java.time.Instant claimedAt = java.time.Instant.parse("2027-01-01T00:00:00Z");
            java.time.Clock clock = java.time.Clock.fixed(claimedAt, java.time.ZoneOffset.UTC);
            SendDeadline fresh = new SendDeadline(clock, claimedAt.plus(mail.claimLease()), mail.attemptTimeout());
            assertThat(fresh.expired()).isFalse();
            assertThat(fresh.remaining()).isBetween(Duration.ofSeconds(119), Duration.ofSeconds(120));
        });
    }

    @ParameterizedTest(name = "{0}={1} stops the application")
    @CsvSource({
        "connect-timeout,PT0S",
        "connect-timeout,PT0.0004S",
        "connect-timeout,-PT1S",
        "connect-timeout,PT600H",
        "read-timeout,PT0S",
        "read-timeout,PT0.0004S",
        "read-timeout,-PT1S",
        "read-timeout,PT600H",
        "attempt-timeout,PT0S",
        "attempt-timeout,PT0.0004S",
        "attempt-timeout,PT0.0000001S",
        "attempt-timeout,PT600H",
        "attempt-timeout,-PT1S",
        "claim-lease,PT0.0004S"
    })
    void timeoutsMustBePositiveWholeMillisecondsThatFitAnInt(String name, String value) {
        runner.withPropertyValues("thi360.mail." + name + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("thi360.mail." + name);
        });
    }

    @Test
    void theSmallestAndLargestProviderTimeoutsAreAccepted() {
        runner.withPropertyValues("thi360.mail.connect-timeout=PT0.001S", "thi360.mail.read-timeout=PT596H")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "1")
                            .containsEntry("mail.smtp.timeout", Long.toString(Duration.ofHours(596).toMillis()));
                });
    }

    /** R4-01: below the 1 s minimum every claim would be claim-lost, so startup rejects it; exactly 1 s starts. */
    @ParameterizedTest(name = "attempt-timeout={0} stops the application")
    @ValueSource(strings = {"PT0.001S", "PT0.999S"})
    void anAttemptTimeoutBelowTheUsableMinimumStopsStartup(String value) {
        runner.withPropertyValues("thi360.mail.attempt-timeout=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("thi360.mail.attempt-timeout");
        });
    }

    @Test
    void theMinimumAttemptTimeoutStartsAndLeavesAFreshClaimUsableBudget() {
        runner.withPropertyValues("thi360.mail.attempt-timeout=PT1S", "thi360.mail.claim-lease=PT6S").run(context -> {
            assertThat(context).hasNotFailed();
            MailProperties mail = context.getBean(MailProperties.class);
            java.time.Instant claimedAt = java.time.Instant.parse("2027-01-01T00:00:00Z");
            java.time.Clock clock = java.time.Clock.fixed(claimedAt, java.time.ZoneOffset.UTC);
            SendDeadline fresh = new SendDeadline(clock, claimedAt.plus(mail.claimLease()), mail.attemptTimeout());
            assertThat(fresh.expired()).isFalse();
            assertThat(fresh.remainingMillis()).isBetween(900, 1000);
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

    /** R1-11: mật khẩu tới được bộ gửi (SmtpMailTransport kết nối bằng chính bộ gửi này). Giá trị giả, và không đưa vào thông báo lỗi của test. */
    @Test
    void thePasswordReachesTheSenderAndTheTransport() {
        String password = "dev-only-" + UUID.randomUUID();
        runner.withPropertyValues("thi360.mail.tls=none", "spring.mail.username=mailer", "spring.mail.password=" + password)
                .run(context -> {
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(password.equals(sender.getPassword())).as("password handed to the sender").isTrue();
                    assertThat(sender.getUsername()).isEqualTo("mailer");
                });
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
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
