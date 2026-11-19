package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Hạn cứng của một lần gửi (R1-05, R2-01, R2-02, R2-03), với hạn thật ngắn để đi qua kẽ hở giữa
 * "DATA được nhận" và "đóng kết nối xong". Máy chủ kịch bản cung cấp vận chuyển thật tới socket,
 * nên mọi lần gửi đều chạy qua {@link SmtpMailTransport} thật.
 */
@TestPropertySource(properties = {"thi360.mail.attempt-timeout=PT3S", "thi360.mail.claim-lease=PT8S"})
class MailDeadlineIT extends AbstractMailIT {

    /** Spy: test đứng được ngay sau commit giữ chỗ. */
    @MockitoSpyBean
    private EmailMessageStore store;

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    /**
     * Máy chủ nhận DATA rồi câm ở QUIT. Thời gian chờ đọc (30 giây) dài hơn hạn một lần gửi (3 giây):
     * nếu kết quả phải chờ đóng kết nối, lần gửi sẽ hết hạn và thư được chấp nhận vẫn bị gửi lại.
     * Đúng ra: DATA được chấp nhận là kết quả cuối — thư ghi "đã gửi", QUIT chỉ là dọn socket.
     */
    @Test
    void aQuitThatStallsPastTheDeadlineStillRecordsTheAcceptedSend() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().silentOnQuit()) {
            routeSmtpTo(server.port(), 30000);
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch())
                    .as("the accepted DATA counts as sent without waiting for QUIT")
                    .isEqualTo(1);

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.sentAt()).isEqualTo(START);
            assertThat(mail.attempts()).isEqualTo(1);
            assertThat(mail.lastError()).isNull();

            advance(Duration.ofDays(1));
            assertThat(dispatcher.dispatchBatch()).isZero();
            assertThat(server.dataCommands.get()).as("the accepted mail is never sent again").isEqualTo(1);
        }
    }

    /**
     * Lần gửi tới hạn phải thật sự bị hủy: kết nối đầu đóng ngay (không có thư nào "đi muộn" sau
     * hạn), và khi máy chủ hết treo thì chỉ lần gửi lại mới đưa thư tới đích — đúng một DATA được
     * chấp nhận.
     */
    @Test
    void aTimedOutAttemptClosesItsConnectionAndCannotDeliverLate() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().stallBeforeDataReply()) {
            routeSmtpTo(server.port());
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            assertThat(dispatcher.dispatchBatch())
                    .as("the stalled attempt is cut at the 3-second deadline")
                    .isZero();
            assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).lastError()).isEqualTo("timeout");
            assertThat(server.dataCommands.get()).isEqualTo(1);
            assertThat(server.dataAccepted.get()).as("the stalled mail was never accepted").isZero();

            // Kết nối đầu phải chết (virtual thread bị ngắt làm đóng socket) trước khi thả máy chủ.
            server.releaseStalledData();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() ->
                            assertThat(server.connectionsClosed.get()).as("the first connection is gone").isEqualTo(1));
            assertThat(server.dataAccepted.get())
                    .as("a connection closed before the DATA terminator is never counted as accepted")
                    .isZero();

            advance(Duration.ofMinutes(1));
            assertThat(dispatcher.dispatchBatch()).as("the retry delivers").isEqualTo(1);

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.sentAt()).isEqualTo(START.plus(Duration.ofMinutes(1)));
            assertThat(mail.attempts()).isEqualTo(2);
            assertThat(server.dataAccepted.get()).as("exactly one accepted DATA, from the retry").isEqualTo(1);
            assertThat(server.dataCommands.get()).isEqualTo(2);
        }
    }

    /**
     * Hạn của lần gửi không bao giờ dài hơn giữ chỗ (R1-01 phần hạn, R3-02). Giữ chỗ 8 giây, khoảng an toàn 5
     * giây, nên hạn của lần gửi là 3 giây kể từ lúc giữ chỗ; worker bị đứng 2 giây sau commit giữ chỗ nên chỉ còn
     * 1 giây, ngắn hơn cả {@code attempt-timeout} (3 giây) lẫn thời gian chờ đọc (30 giây). Máy chủ treo ở DATA: lần
     * gửi phải bị cắt sau khoảng 1 giây (không phải 3), và thời gian chờ socket truyền cho vận chuyển không vượt
     * phần còn lại. Bỏ kẹp theo giữ chỗ thì test này đỏ.
     */
    @Test
    void deadlineNeverOutlivesTheLease() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer().stallBeforeDataReply()) {
            routeSmtpTo(server.port(), 30000);
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);
            doAnswer(invocation -> {
                        Object claimed = invocation.callRealMethod();
                        advance(Duration.ofSeconds(2)); // worker đứng 2 giây sau commit giữ chỗ
                        return claimed;
                    })
                    .when(store)
                    .claimNext(any(), any(), any());

            long startedAt = System.nanoTime();
            int sent = dispatcher.dispatchBatch();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(sent).isZero();
            assertThat(elapsed)
                    .as("cut at the 1 s left of the claim, not at the 3 s attempt cap")
                    .isLessThan(Duration.ofMillis(2500));
            assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).lastError()).isEqualTo("timeout");
            assertThat(scriptedSessions).hasSize(1);
            Properties socket = scriptedSessions.get(0).getProperties();
            for (String key : List.of("connectiontimeout", "timeout", "writetimeout")) {
                assertThat(Long.parseLong(socket.getProperty("mail.smtp." + key)))
                        .as("socket %s stays within the 1 s left of the claim", key)
                        .isBetween(1L, 1000L);
            }
        }
    }

    /**
     * R4-02: hạn tuyệt đối hết trong lúc kiểm tra quyền giữ còn treo, trước khi SMTP bắt đầu. Phải xử lý như
     * {@code claim-lost}: không có lời gọi vận chuyển, không tính lần gửi, không ghi {@code timeout}, không tốn
     * lượt gửi lại. Luồng gửi bị hủy mà vẫn chạy tiếp khi kiểm tra được thả thì cũng không được mở SMTP; lần lấy
     * lại hợp lệ kế tiếp gửi đúng một thư.
     */
    @Test
    void aCutoffThatPassesWhileTheHoldCheckIsPendingIsClaimLostNotATimeout() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch checkReturned = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<Thread> sendThread = new AtomicReference<>();
        doAnswer(invocation -> {
                    if (checks.incrementAndGet() == 1) {
                        sendThread.set(Thread.currentThread());
                        // Treo qua hạn 3 giây và bỏ qua lệnh ngắt, như một truy vấn bị kẹt không đáp lại ngắt.
                        while (release.getCount() > 0) {
                            try {
                                release.await();
                            } catch (InterruptedException ignored) {
                                // vẫn treo tới khi được thả
                            }
                        }
                        Object held = invocation.callRealMethod();
                        checkReturned.countDown();
                        return held;
                    }
                    return invocation.callRealMethod();
                })
                .when(store)
                .stillHeld(any(), any());

        try {
            assertThat(dispatcher.dispatchBatch()).as("nothing was sent").isZero();

            MailOutbox.Mail afterCutoff = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(afterCutoff.lastError()).as("no timeout record for an attempt that never started").isNull();
            assertThat(afterCutoff.attempts()).as("no attempt counted, no retry spent").isZero();
            assertThat(afterCutoff.sentAt()).isNull();
            assertThat(afterCutoff.failedAt()).isNull();
        } finally {
            // Thả kiểm tra kể cả khi đỏ: luồng gửi treo không đáp lại ngắt nên phải luôn được thả.
            release.countDown();
        }

        // Đợi lượt gửi đã hủy kết thúc hẳn trước khi đếm lời gọi vận chuyển: một lượt bị hủy mà vẫn
        // chạy tiếp khi được thả không được mở SMTP.
        assertThat(sendThread.get()).isNotNull();
        sendThread.get().join(Duration.ofSeconds(10));
        assertThat(sendThread.get().isAlive()).as("the cancelled send task exits").isFalse();
        assertThat(checkReturned.await(10, TimeUnit.SECONDS)).isTrue();
        verify(transport, never()).send(any(), any(), any(), any(), any());

        advance(Duration.ofMinutes(1));
        assertThat(dispatcher.dispatchBatch()).as("the next legitimate claim delivers").isEqualTo(1);

        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.sentAt()).isNotNull();
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.lastError()).isNull();
        verify(transport, times(1)).send(any(), any(), any(), any(), any());
        assertThat(receivedAtLeast(1)).extracting(Received::to).containsExactly(to);
        assertThat(receivedCount()).as("exactly one delivery").isEqualTo(1);
    }

    /**
     * R5-01: người gọi dispatch bị ngắt (worker đang tắt, ví dụ {@code shutdownNow()}) trong lúc hàng rào
     * kiểm tra quyền giữ còn treo, trước khi SMTP bắt đầu. Người gọi trả về nhưng lượt gửi bị hủy mà vẫn chạy
     * tiếp khi được thả không được mở SMTP và không ghi kết quả; lần lấy lại hợp lệ kế tiếp gửi đúng một thư.
     */
    @Test
    void callerInterruptBeforeFenceReturnsNeverStartsSmtp() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch checkEntered = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<Thread> sendThread = new AtomicReference<>();
        doAnswer(invocation -> {
                    if (checks.incrementAndGet() == 1) {
                        sendThread.set(Thread.currentThread());
                        checkEntered.countDown();
                        // Treo và bỏ qua lệnh ngắt, như một truy vấn bị kẹt không đáp lại ngắt.
                        while (release.getCount() > 0) {
                            try {
                                release.await();
                            } catch (InterruptedException ignored) {
                                // vẫn treo tới khi được thả
                            }
                        }
                    }
                    return invocation.callRealMethod();
                })
                .when(store)
                .stillHeld(any(), any());

        AtomicInteger sent = new AtomicInteger(-1);
        Thread caller = Thread.ofVirtual()
                .name("mail-dispatch-caller")
                .start(() -> sent.set(dispatcher.dispatchBatch()));
        assertThat(checkEntered.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            caller.interrupt();
            caller.join(Duration.ofSeconds(10));
            assertThat(caller.isAlive()).as("the interrupted dispatch returns").isFalse();
            assertThat(sent.get()).as("nothing was sent").isZero();

            MailOutbox.Mail afterInterrupt = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(afterInterrupt.lastError()).as("no result written").isNull();
            assertThat(afterInterrupt.attempts()).as("no attempt counted, no retry spent").isZero();
            assertThat(afterInterrupt.sentAt()).isNull();
            assertThat(afterInterrupt.failedAt()).isNull();
        } finally {
            release.countDown();
        }

        // Thả hàng rào: luồng gửi đã bị người gọi hủy phải kết thúc mà không mở SMTP.
        assertThat(sendThread.get()).isNotNull();
        sendThread.get().join(Duration.ofSeconds(10));
        assertThat(sendThread.get().isAlive()).as("the cancelled send task exits").isFalse();
        verify(transport, never()).send(any(), any(), any(), any(), any());

        advance(Duration.ofMinutes(1));
        assertThat(dispatcher.dispatchBatch()).as("the next legitimate claim delivers").isEqualTo(1);

        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.sentAt()).isNotNull();
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.lastError()).isNull();
        verify(transport, times(1)).send(any(), any(), any(), any(), any());
        assertThat(receivedAtLeast(1)).extracting(Received::to).containsExactly(to);
        assertThat(receivedCount()).as("exactly one delivery").isEqualTo(1);
    }

    /**
     * Máy chủ từ chối mọi người nhận bằng {@code 550} rồi câm ở {@code RSET} (R2-01). Angus đọc trả lời {@code RSET}
     * trước khi báo lỗi {@code 550} đã biết; thời gian chờ đọc (30 giây) dài hơn hạn một lần gửi (3 giây) nên nếu
     * còn chờ thì kết quả vĩnh viễn bị đổi thành "timeout" và thư gửi lại. Đúng ra: {@code failed_at} ngay lần
     * đầu, một {@code RCPT}, không chờ {@code RSET}.
     */
    @Test
    void aRejectedRecipientStaysPermanentWhenTheServerNeverAnswersRset() throws Exception {
        try (ScriptedSmtpServer server =
                new ScriptedSmtpServer().rejectRecipientWith("550 5.1.1 No such user").silentOnRset()) {
            routeSmtpTo(server.port(), 30000);
            String to = MailSamples.randomAddress();
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);

            long startedAt = System.nanoTime();
            assertThat(dispatcher.dispatchBatch()).isZero();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(elapsed).as("the 550 is not held back by RSET").isLessThan(Duration.ofSeconds(2));
            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.lastError()).isEqualTo("smtp-550 5.1.1 rcpt-rejected");
            assertThat(mail.failedAt()).isEqualTo(START);
            assertThat(mail.attempts()).isEqualTo(1);

            advance(Duration.ofDays(1));
            assertThat(dispatcher.dispatchBatch()).isZero();
            assertThat(server.rcptCommands.get()).as("no second RCPT after the permanent failure").isEqualTo(1);
        }
    }
}
