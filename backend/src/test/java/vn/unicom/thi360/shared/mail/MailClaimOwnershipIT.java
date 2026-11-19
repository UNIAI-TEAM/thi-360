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
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Quyền giữ thư của worker (R1-01, R1-02, R1-13, R1-14). Hai {@code MailDispatcher} chạy xen kẽ một cách xác định:
 * worker A bị giữ ở ranh giới SMTP (lời gọi {@link MailTransport#send}), đồng hồ tiêm vào được tua qua hạn giữ chỗ,
 * và trong lúc A còn đang gửi thì worker B chạy trọn một lượt ngay trong lời gọi đó.
 *
 * <p>Lớp này chỉ dùng {@code MailDispatcher}, {@code MailTransport}, JDBC và đồng hồ, để chạy được cả trên bản
 * cũ của worker khi cần chứng minh test đỏ.
 */
class MailClaimOwnershipIT extends AbstractMailIT {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private MailProperties properties;

    /** Spy để test đứng được giữa commit giữ chỗ và bước kiểm tra lại quyền giữ trước SMTP. */
    @MockitoSpyBean
    private EmailMessageStore store;

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    /**
     * Kẽ hở của R1-01/R1-13: worker A commit giữ chỗ xong thì bị đứng; đồng hồ tua qua hạn giữ chỗ và
     * worker B lấy lại thư, gửi và ghi kết quả — tất cả trước khi A tới bước nộp việc gửi. A phải phát
     * hiện chỗ đã mất khi kiểm tra lại ngay trước SMTP và không bao giờ bắt đầu phiên SMTP: toàn cục
     * chỉ đúng một lần {@link MailTransport#send} (của B). Trên bản cũ (không có kiểm tra này) kịch bản
     * tương đương đứng ở lời gọi vận chuyển và A gửi lần thứ hai.
     */
    @Test
    void expiredClaimantNeverStartsSmtp() throws Exception {
        String to = MailSamples.randomAddress();
        enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        MailDispatcher other = new MailDispatcher(store, transport, properties, clock, transactionManager);
        AtomicInteger transportCalls = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        doAnswer(invocation -> {
                    transportCalls.incrementAndGet();
                    return invocation.callRealMethod();
                })
                .when(transport)
                .send(any(), any(), any(), any());
        doAnswer(invocation -> {
                    if (checks.incrementAndGet() == 1) {
                        // A đứng giữa commit giữ chỗ và nộp việc gửi: quá hạn, B lấy lại và gửi xong.
                        advance(LEASE.plusMinutes(1));
                        other.dispatchBatch();
                    }
                    return invocation.callRealMethod();
                })
                .when(store)
                .stillHeld(any(), any());

        assertThat(dispatcher.dispatchBatch()).as("A sends nothing: its claim was already taken").isZero();

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
                .when(transport)
                .send(any(), any(), any(), any());

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
                        return stale.play(invocation, to); // rồi mới tới kết quả muộn của A
                    }
                    return newer.play(invocation, to);
                })
                .when(transport)
                .send(any(), any(), any(), any());

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
                .when(transport)
                .send(any(), any(), any(), any());

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

    enum Outcome {
        SENT,
        TRANSIENT,
        PERMANENT;

        Object play(org.mockito.invocation.InvocationOnMock invocation, String to) throws Throwable {
            return switch (this) {
                case SENT -> invocation.callRealMethod();
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
