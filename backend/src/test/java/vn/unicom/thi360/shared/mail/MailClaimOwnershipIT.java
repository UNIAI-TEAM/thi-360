package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Quyền giữ thư của worker (R1-01, R1-02, R1-13, R1-14, R3-02). Hai {@code MailDispatcher} chạy xen kẽ một cách xác
 * định: worker A bị giữ ở ranh giới gửi (kiểm tra quyền giữ trên luồng gửi hoặc lời gọi {@link MailTransport#send}),
 * đồng hồ tiêm vào được tua qua hạn giữ chỗ, và trong lúc A còn đang đứng thì worker B chạy trọn một lượt ngay đó.
 *
 * <p>Lớp này chỉ dùng {@code MailDispatcher}, {@code MailTransport}, JDBC và đồng hồ.
 */
class MailClaimOwnershipIT extends AbstractMailIT {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private MailProperties properties;

    @Autowired
    private JavaMailSenderImpl sender;

    /** Spy để test đứng được giữa commit giữ chỗ và bước kiểm tra lại quyền giữ trước SMTP. */
    @MockitoSpyBean
    private EmailMessageStore store;

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    /**
     * Phép thử của reviewer (R1-01/R1-13, R3-02): worker A qua bước kiểm tra mã giữ chỗ thành công, rồi bị đứng
     * <em>sau</em> bước đó và trước SMTP; đồng hồ tua qua hạn giữ chỗ, worker B lấy lại thư, gửi và ghi kết quả; A
     * chạy tiếp. A không được mở phiên SMTP thứ hai: hạn tuyệt đối lấy lại sau bước kiểm tra nên thời gian đứng bị
     * trừ vào hạn, và A bỏ lượt. Toàn cục chỉ đúng một lần {@link MailTransport#send} (của B). Kiểm tra chạy trên
     * luồng gửi, trước lời gọi vận chuyển.
     */
    @Test
    void pauseAfterCheckBeforeSendStillSendsOnce() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        MailDispatcher other = new MailDispatcher(store, transport, properties, clock, transactionManager);
        Thread caller = Thread.currentThread();
        AtomicInteger transportCalls = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<String> checkThread = new AtomicReference<>();
        AtomicInteger transportCallsAtCheck = new AtomicInteger(-1);
        doAnswer(invocation -> {
                    transportCalls.incrementAndGet();
                    return invocation.callRealMethod();
                })
                .when(transport)
                .send(any(), any(), any(), any(), any());
        doAnswer(invocation -> {
                    boolean held = (boolean) invocation.callRealMethod();
                    if (checks.incrementAndGet() == 1) {
                        checkThread.set(Thread.currentThread().getName());
                        transportCallsAtCheck.set(transportCalls.get());
                        // Chờ cho tới khi lời gọi của A đã vào bước chờ kết quả (đã đo xong thời gian chờ).
                        Awaitility.await()
                                .atMost(Duration.ofSeconds(10))
                                .until(() -> caller.getState() == Thread.State.TIMED_WAITING);
                        // Kiểm tra đã qua (A còn giữ thư). A đứng ở đây: quá hạn, B lấy lại và gửi xong.
                        assertThat(held).as("A still holds the mail when the check runs").isTrue();
                        advance(LEASE.plusMinutes(1));
                        other.dispatchBatch();
                    }
                    return held;
                })
                .when(store)
                .stillHeld(any(), any());

        assertThat(dispatcher.dispatchBatch()).as("A sends nothing: its deadline passed while it paused").isZero();

        assertThat(checkThread.get()).as("the check runs on the send thread").startsWith("mail-send-");
        assertThat(transportCallsAtCheck.get()).as("the check precedes the transport call").isZero();
        assertThat(transportCalls.get()).as("exactly one SMTP transaction, opened by B").isEqualTo(1);
        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.sentAt()).isNotNull();
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(receivedAtLeast(1)).extracting(Received::to).containsExactly(to);
    }

    /**
     * Thư thứ hai của lô gửi lâu (4 phút, vẫn trong hạn giữ chỗ 5 phút của chính nó) và hạn giữ chỗ nguyên lô ở
     * bản cũ đã hết khi worker B chạy. Thư thứ ba chưa tới lượt A nên B phải lấy được đúng một lần; thư thứ hai A vẫn
     * đang giữ nên B không được đụng tới.
     */
    @Test
    void aSlowWorkerKeepsOnlyTheMailItIsSendingAndNeverSendsATailMailTwice() throws Exception {
        List<String> recipients = enqueueInOrder(3);
        Instant t0 = clock.instant();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger bSent = new AtomicInteger(-1);
        doAnswer(invocation -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        advance(Duration.ofMinutes(4)); // thư 1 gửi lâu nhưng chưa quá hạn giữ chỗ của nó
                    } else if (call == 2) {
                        advance(Duration.ofMinutes(4)); // quá hạn giữ chỗ nguyên lô (t0 + 5 phút) của bản cũ
                        bSent.set(dispatcher.dispatchBatch()); // B chạy trọn một lượt khi A đang gửi thư 2
                    }
                    return invocation.callRealMethod();
                })
                .when(transport).send(any(), any(), any(), any(), any());

        int aSent = dispatcher.dispatchBatch();

        assertThat(clock.instant()).isEqualTo(t0.plus(Duration.ofMinutes(8)));
        assertThat(bSent.get()).as("B takes the mail A has not started yet, and only that").isEqualTo(1);
        assertThat(aSent).as("A sends the two mails it actually claimed").isEqualTo(2);
        assertThat(receivedAtLeast(3)).extracting(Received::to).containsExactlyInAnyOrderElementsOf(recipients);
        assertThat(receivedCount()).as("every mail delivered exactly once").isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM shared.email_message WHERE sent_at IS NOT NULL AND attempts = 1",
                        Integer.class))
                .isEqualTo(3);
    }

    /**
     * Worker A mất quyền giữ thư giữa chừng (bị đứng quá hạn); worker B lấy lại và ghi kết quả. Kết quả muộn của A, dù
     * thành công hay lỗi, không được đè kết quả của B: trạng thái, số lần gửi và hạn gửi lại giữ nguyên của B.
     */
    @ParameterizedTest(name = "newer claimant {0}, stale claimant {1}")
    @CsvSource({
        "SENT,SENT",
        "SENT,TRANSIENT",
        "SENT,PERMANENT",
        "PERMANENT,SENT",
        "PERMANENT,TRANSIENT",
        "PERMANENT,PERMANENT",
        "TRANSIENT,SENT",
        "TRANSIENT,TRANSIENT",
        "TRANSIENT,PERMANENT"
    })
    void aStaleClaimantCannotOverwriteTheNewerOutcome(Outcome newer, Outcome stale) throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<MailOutbox.Mail> afterNewer = new AtomicReference<>();
        doAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        advance(LEASE.plusMinutes(1)); // A quá hạn giữ chỗ
                        dispatcher.dispatchBatch(); // B lấy lại và ghi kết quả
                        afterNewer.set(outbox.require(to, MailKind.EMAIL_VERIFY));
                        return stale.play(invocation, to, () -> lateRealSend(invocation)); // rồi mới tới kết quả muộn của A
                    }
                    return newer.play(invocation, to, invocation::callRealMethod);
                })
                .when(transport).send(any(), any(), any(), any(), any());

        dispatcher.dispatchBatch();

        assertThat(calls.get()).as("A sent once and B once").isEqualTo(2);
        MailOutbox.Mail kept = afterNewer.get();
        assertThat(kept.attempts()).isEqualTo(1);
        switch (newer) {
            case SENT -> assertThat(kept.sentAt()).isNotNull();
            case PERMANENT -> assertThat(kept.failedAt()).isNotNull();
            case TRANSIENT -> {
                assertThat(kept.sentAt()).isNull();
                assertThat(kept.failedAt()).isNull();
                assertThat(kept.nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
            }
        }
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY))
                .as("A's late %s result must not change what B recorded", stale)
                .isEqualTo(kept);
        assertThat(kept.sentAt() != null && kept.failedAt() != null).as("never both sent and failed").isFalse();
    }

    /** Chủ thư thật sự chết (không ghi kết quả): thư vẫn bị giữ tới hết hạn, rồi worker khác gửi. */
    @Test
    void aMailWhoseOwnerReallyStoppedIsSentAfterTheLease() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new ProcessDied();
                    }
                    return invocation.callRealMethod();
                })
                .when(transport).send(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> dispatcher.dispatchBatch()).isInstanceOf(ProcessDied.class);
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt()).isNull();

        advance(LEASE.minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).as("still held a second before the lease ends").isZero();
        advance(Duration.ofSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.sentAt()).isEqualTo(clock.instant());
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(receivedAtLeast(1)).extracting(Received::to).containsExactly(to);
    }

    /**
     * SMTP của A đã chạy trước khi hạn giữ chỗ trôi qua và chỉ kết thúc sau đó (phần còn lại chấp nhận được, AD-19):
     * gửi thật bằng hạn của một lần gửi còn sống.
     */
    private Object lateRealSend(org.mockito.invocation.InvocationOnMock invocation) throws Exception {
        SendDeadline inFlight = new SendDeadline(clock, clock.instant().plus(LEASE), properties.attemptTimeout());
        new SmtpMailTransport(sender, properties.from(), clock)
                .send(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        inFlight);
        return null;
    }

    enum Outcome {
        SENT,
        TRANSIENT,
        PERMANENT;

        Object play(org.mockito.invocation.InvocationOnMock invocation, String to, ThrowingSupplier<Object> realSend)
                throws Throwable {
            return switch (this) {
                case SENT -> realSend.get();
                case TRANSIENT -> throw new SMTPSendFailedException("DATA", 451, "451 4.3.0 Try again later", null, null, null, null);
                case PERMANENT -> throw new SMTPAddressFailedException(new InternetAddress(to), "RCPT TO", 550, "550 5.1.1 No such user");
            };
        }
    }

    private static final class ProcessDied extends Error {
        ProcessDied() {
            super("simulated process death", null, false, false);
        }
    }

    /** Xếp {@code count} thư lần lượt cách nhau một giây (thứ tự gửi xác định), rồi đặt đồng hồ sau thư cuối. */
    private List<String> enqueueInOrder(int count) {
        List<String> recipients = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String to = MailSamples.randomAddress();
            recipients.add(to);
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);
            advance(Duration.ofSeconds(1));
        }
        return recipients;
    }
}
