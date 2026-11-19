package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.MDC;
import vn.unicom.thi360.shared.web.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.support.MailOutbox;

@ExtendWith(OutputCaptureExtension.class)
class MailDispatchIT extends AbstractMailIT {

    private record Sent(MailKind kind, String locale, String to) {
    }

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    @Test
    void deliversSubjectHtmlAndTextPerLocale() throws Exception {
        List<Sent> sent = new ArrayList<>();
        for (MailKind kind : MailKind.values()) {
            for (String locale : List.of("vi", "en")) {
                String to = MailSamples.randomAddress();
                enqueue(kind, locale, to);
                sent.add(new Sent(kind, locale, to));
            }
        }

        assertThat(dispatcher.dispatchBatch()).isEqualTo(sent.size());

        Received[] received = receivedAtLeast(sent.size());
        assertThat(received).hasSize(sent.size());
        Map<String, String> viSubjects = new java.util.HashMap<>();
        for (Sent expected : sent) {
            Received mail = java.util.Arrays.stream(received)
                    .filter(r -> r.to().equals(expected.to()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Nothing delivered to " + expected));
            String where = expected.kind() + "/" + expected.locale();

            assertThat(mail.subject()).as("subject of %s", where).isEqualTo(firstLineOfTemplate(expected));
            assertThat(mail.text()).as("text of %s", where).isNotBlank().doesNotContain(mail.subject());
            assertThat(mail.html()).as("html of %s", where).contains("<html").contains("lang=\"" + expected.locale() + "\"");
            for (String value : MailSamples.visibleValues(expected.kind())) {
                assertThat(mail.text()).as("text of %s shows '%s'", where, value).contains(value);
                assertThat(mail.html()).as("html of %s shows '%s'", where, value).contains(htmlOf(value));
            }
            if (expected.locale().equals("vi")) {
                viSubjects.put(expected.kind().name(), mail.subject());
            } else {
                assertThat(mail.subject()).as("en subject differs from vi for %s", expected.kind())
                        .isNotEqualTo(viSubjects.get(expected.kind().name()));
            }
        }
    }

    @Test
    void permanentRcptFailureFailsAtOnce() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().rejectRecipientWith("550 5.1.1 No such user")) {
            routeSmtpTo(server.port());
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch()).isZero();

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.failedAt()).isEqualTo(START);
            assertThat(mail.sentAt()).isNull();
            assertThat(mail.attempts()).isEqualTo(1);
            assertThat(mail.lastError()).isEqualTo("smtp-550 5.1.1 rcpt-rejected");

            advance(Duration.ofDays(1));
            assertThat(dispatcher.dispatchBatch()).isZero();
            assertThat(server.rcptCommands.get()).as("no second attempt after a permanent failure").isEqualTo(1);
        }
    }

    @Test
    void transientFailureIsRescheduled() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        List<Duration> expectedDelays =
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

        try (ScriptedSmtpServer server = new ScriptedSmtpServer().rejectRecipientWith("451 4.3.0 Try again later")) {
            routeSmtpTo(server.port());

            for (int failure = 1; failure <= expectedDelays.size(); failure++) {
                Instant failedAt = clock.instant();
                assertThat(dispatcher.dispatchBatch()).isZero();

                MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
                Duration delay = expectedDelays.get(failure - 1);
                assertThat(mail.attempts()).isEqualTo(failure);
                assertThat(mail.failedAt()).as("still retryable after failure #%d", failure).isNull();
                assertThat(mail.nextAttemptAt()).isEqualTo(failedAt.plus(delay));
                assertThat(mail.lastError()).isEqualTo("smtp-451 4.3.0 rcpt-deferred");

                // Một giây trước hạn: chưa được lấy lại; đúng hạn: lấy lại.
                advance(delay.minusSeconds(1));
                int before = server.rcptCommands.get();
                assertThat(dispatcher.dispatchBatch()).isZero();
                assertThat(server.rcptCommands.get()).as("not due yet after failure #%d", failure).isEqualTo(before);
                advance(Duration.ofSeconds(1));
            }
        }
    }

    @Test
    void unreachableServerIsRetriedToo() throws Exception {
        routeSmtpTo(closedPort());
        String to = MailSamples.randomAddress();
        enqueue(MailKind.PASSWORD_RESET, "en", to);

        assertThat(dispatcher.dispatchBatch()).isZero();

        MailOutbox.Mail mail = outbox.require(to, MailKind.PASSWORD_RESET);
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.failedAt()).isNull();
        assertThat(mail.nextAttemptAt()).isEqualTo(START.plus(Duration.ofMinutes(1)));
        assertThat(mail.lastError()).isEqualTo("connect-failed");
    }

    @Test
    void fifthFailureMarksFailed() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);

        try (ScriptedSmtpServer server = new ScriptedSmtpServer().rejectRecipientWith("452 4.2.2 Mailbox full")) {
            routeSmtpTo(server.port());

            for (int attempt = 1; attempt <= 5; attempt++) {
                assertThat(dispatcher.dispatchBatch()).isZero();
                MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
                assertThat(mail.attempts()).isEqualTo(attempt);
                if (attempt < 5) {
                    assertThat(mail.failedAt()).as("attempt #%d is not the last", attempt).isNull();
                }
                advance(Duration.ofHours(3));
            }

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.failedAt()).isNotNull();
            assertThat(mail.sentAt()).isNull();
            assertThat(mail.attempts()).isEqualTo(5);

            int rcptBefore = server.rcptCommands.get();
            advance(Duration.ofDays(30));
            assertThat(dispatcher.dispatchBatch()).isZero();
            assertThat(server.rcptCommands.get()).as("a failed mail is never taken again").isEqualTo(rcptBefore);
            assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).attempts()).isEqualTo(5);
        }
    }

    @Test
    void concurrentWorkersDoNotSendTwice() throws Exception {
        int total = 60;
        Set<String> recipients = new HashSet<>();
        for (int i = 0; i < total; i++) {
            String to = MailSamples.randomAddress();
            recipients.add(to);
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        }

        int workers = 3;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CyclicBarrier start = new CyclicBarrier(workers);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    int sent = 0;
                    int batch;
                    while ((batch = dispatcher.dispatchBatch()) > 0) {
                        sent += batch;
                    }
                    return sent;
                }));
            }
            int sentByWorkers = 0;
            for (Future<Integer> result : results) {
                sentByWorkers += result.get(60, TimeUnit.SECONDS);
            }
            assertThat(sentByWorkers).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }

        Received[] received = receivedAtLeast(total);
        assertThat(received).as("every mail delivered exactly once").hasSize(total);
        assertThat(java.util.Arrays.stream(received).map(Received::to)).containsExactlyInAnyOrderElementsOf(recipients);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM shared.email_message WHERE sent_at IS NOT NULL AND attempts = 1",
                        Integer.class))
                .isEqualTo(total);
    }

    @Test
    void batchTakesAtMostTwenty() {
        for (int i = 0; i < 25; i++) {
            enqueue(MailKind.EMAIL_VERIFY, "vi", MailSamples.randomAddress());
        }

        assertThat(dispatcher.dispatchBatch()).isEqualTo(20);
        assertThat(receivedAtLeast(20)).hasSize(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.email_message WHERE sent_at IS NULL", Integer.class))
                .isEqualTo(5);

        assertThat(dispatcher.dispatchBatch()).isEqualTo(5);
        assertThat(receivedAtLeast(25)).hasSize(25);
        assertThat(dispatcher.dispatchBatch()).isZero();
    }

    @Test
    void smtpRunsOutsideTransaction() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicBoolean claimCommittedBeforeSend = new AtomicBoolean(false);
        doAnswer(invocation -> {
                    transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive()
                            || TransactionSynchronizationManager.isSynchronizationActive());
                    // Đọc bằng kết nối khác: chỉ thấy giữ chỗ khi transaction lấy thư đã commit.
                    Instant heldUntil = jdbc.queryForObject(
                                    "SELECT next_attempt_at FROM shared.email_message WHERE lower(to_email) = lower(?)",
                                    java.sql.Timestamp.class,
                                    to)
                            .toInstant();
                    claimCommittedBeforeSend.set(heldUntil.isAfter(clock.instant()));
                    return invocation.callRealMethod();
                })
                .when(transport).send(any(), any(), any(), any(), any());

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        assertThat(transactionActive).isFalse();
        assertThat(claimCommittedBeforeSend).as("claim is committed before SMTP starts").isTrue();
        assertThat(receivedAtLeast(1)).hasSize(1);
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).isEqualTo(START);
    }

    @Test
    void logsAndLastErrorCarryNoBody(CapturedOutput output) throws Exception {
        String code = MailSamples.randomCode();
        String to = MailSamples.randomAddress();
        tx.executeWithoutResult(status ->
                queue.enqueue(MailKind.EMAIL_VERIFY, to, "en", MailSamples.model(MailKind.EMAIL_VERIFY, code), null));
        MailOutbox.Mail queued = outbox.require(to, MailKind.EMAIL_VERIFY);

        // Máy chủ lặp lại cả nội dung thư trong câu trả lời lỗi: thân thư và mã OTP nằm trong thông báo của ngoại lệ.
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().echoDataAfter("554 5.6.0 Rejected:")) {
            routeSmtpTo(server.port());

            assertThat(dispatcher.dispatchBatch()).isZero();

            assertThat(server.lastData()).as("the scripted server did receive the body").contains(code);
        }

        MailOutbox.Mail failed = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(failed.lastError()).isEqualTo("smtp-554 5.6.0");
        for (String secret : List.of(code, queued.subject(), to, "Your email verification code")) {
            assertThat(failed.lastError()).doesNotContain(secret);
            assertThat(output.getAll()).as("log must not contain the body").doesNotContain(secret);
        }
        assertThat(output.getAll()).as("the failure was logged").contains("Mail send failed");
    }

    /** R1-05: DATA đã được nhận, lệnh đóng kết nối ({@code QUIT}) không bao giờ được trả lời: thư vẫn là đã gửi. */
    @Test
    void aQuitThatNeverAnswersAfterAcceptedDataIsNotASendFailure() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().silentOnQuit()) {
            routeSmtpTo(server.port(), 1000);
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.sentAt()).isEqualTo(START);
            assertThat(mail.attempts()).isEqualTo(1);
            assertThat(mail.failedAt()).isNull();
            assertThat(mail.lastError()).isNull();

            advance(Duration.ofDays(1));
            assertThat(dispatcher.dispatchBatch()).isZero();
            assertThat(server.dataCommands.get()).as("the accepted mail is never sent again").isEqualTo(1);
        }
    }

    /** R1-06: chỉ mã trả lời và mã trạng thái mở rộng ở đầu câu trả lời vào {@code last_error}. */
    @ParameterizedTest(name = "reply \"{0}\" is stored as \"{1}\"")
    @CsvSource(
            delimiter = '|',
            value = {
                "554 Rejected: body said 5.9.9 and more|smtp-554",
                "554 5.6.0 Rejected: body said 5.9.9 and more|smtp-554 5.6.0",
                "554 4.6.0 status class does not match the reply code|smtp-554",
                "554 x5.6.0 glued to a word|smtp-554",
                "554 5.6.0|smtp-554 5.6.0"
            })
    void lastErrorKeepsOnlyTheReplyCodeAndAValidPrefixStatus(String reply, String expected) throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().answerDataWith(reply)) {
            routeSmtpTo(server.port());
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch()).isZero();

            assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).lastError()).isEqualTo(expected);
        }
    }
    /** R2-04: một lượt giao thật qua máy chủ bắt AUTH, với tài khoản giả {@code dev-only-}. */
    @Test
    void deliveryThroughAnAuthRequiredServer() throws Exception {
        String user = "thi360-test";
        String password = "dev-only-" + UUID.randomUUID();
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().requireAuth(user, password)) {
            routeSmtpTo(server.port(), 5000, user, password);
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

            assertThat(server.authenticatedAs()).isEqualTo(user);
            assertThat(server.lastData()).contains(to);
            assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).isEqualTo(START);
        }
    }

    /** R2-09: trường {@code Date} của MIME đi theo đồng hồ được tiêm, trùng với {@code sent_at}. */
    @Test
    void mimeDateComesFromTheInjectedClock() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        receivedAtLeast(1);
        MimeMessage received = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(received.getSentDate()).isEqualTo(Date.from(START));
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).isEqualTo(START);
    }

    /** R2-10: {@code traceId}/{@code job}/{@code jobId} của lượt đi theo tới luồng vận chuyển. */
    @Test
    void mdcOfTheBatchFollowsTheSendThread(CapturedOutput output) throws Exception {
        AtomicReference<String> trace = new AtomicReference<>();
        AtomicReference<String> job = new AtomicReference<>();
        AtomicReference<String> jobId = new AtomicReference<>();
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
                    trace.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
                    job.set(MDC.get("job"));
                    jobId.set(MDC.get("jobId"));
                    threadName.set(Thread.currentThread().getName());
                    return invocation.callRealMethod();
                })
                .when(transport).send(any(), any(), any(), any(), any());
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        assertThat(threadName.get()).as("send ran on the mail virtual executor").startsWith("mail-send-");
        assertThat(trace.get()).as("traceId present on the transport thread").isNotBlank();
        assertThat(job.get()).isEqualTo("mail");
        assertThat(jobId.get()).isNotBlank();
        assertThat(output.getAll())
                .as("the transport-thread diagnostic carries the batch trace")
                .contains("trace=" + trace.get());
        // MDC của luồng ảo được dọn sau task: các giá trị của lượt không lọt ra ngoài việc gửi.
        assertThat(MDC.get("job")).as("batch MDC is restored after dispatch").isNull();
    }

    private static String firstLineOfTemplate(Sent sent) throws IOException {
        return new ClassPathResource("mail/templates/" + sent.kind().templateName() + "." + sent.locale() + ".txt")
                .getContentAsString(StandardCharsets.UTF_8)
                .lines()
                .findFirst()
                .orElseThrow()
                .strip();
    }

    /** Giá trị như nó hiện trong HTML: chỉ cần thoát các ký tự đặc biệt dùng trong mẫu. */
    private static String htmlOf(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
