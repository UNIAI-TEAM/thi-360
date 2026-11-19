package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.support.MailOutbox;

/** R1-12: một giao dịch ghi kết quả lỗi không làm mất phần còn lại của lô. */
class MailResultWriteIT extends AbstractMailIT {

    @MockitoSpyBean
    private EmailMessageStore store;

    @Autowired
    private MailProperties properties;

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    @Test
    void aFailedResultWriteDoesNotAbandonTheRestOfTheBatch() {
        List<String> recipients = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String to = MailSamples.randomAddress();
            recipients.add(to);
            enqueue(MailKind.EMAIL_VERIFY, "vi", to);
            advance(Duration.ofSeconds(1));
        }
        String unrecorded = recipients.get(1);
        Instant claimedAt = clock.instant();
        doThrow(new TransientDataAccessResourceException("connection lost while recording"))
                .doCallRealMethod()
                .when(store)
                .markSent(argThat(mail -> mail.to().equals(unrecorded)), any(Instant.class));

        assertThat(dispatcher.dispatchBatch()).as("the two mails whose result was written").isEqualTo(2);

        assertThat(outbox.require(recipients.get(0), MailKind.EMAIL_VERIFY).sentAt()).isNotNull();
        assertThat(outbox.require(recipients.get(2), MailKind.EMAIL_VERIFY).sentAt())
                .as("a later mail still commits its success").isNotNull();
        MailOutbox.Mail stuck = outbox.require(unrecorded, MailKind.EMAIL_VERIFY);
        assertThat(stuck.sentAt()).isNull();
        assertThat(stuck.attempts()).isZero();
        assertThat(stuck.nextAttemptAt()).as("still held by the lost claim").isEqualTo(claimedAt.plus(properties.claimLease()));
        assertThat(receivedAtLeast(3)).hasSize(3);

        // Định nghĩa phục hồi: chưa ghi được kết quả thì thư được gửi lại sau hạn giữ chỗ (gửi ít nhất một lần).
        advance(properties.claimLease().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isZero();
        advance(Duration.ofSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        MailOutbox.Mail recovered = outbox.require(unrecorded, MailKind.EMAIL_VERIFY);
        assertThat(recovered.sentAt()).isEqualTo(clock.instant());
        assertThat(recovered.attempts()).isEqualTo(1);
        assertThat(receivedAtLeast(4)).as("the unrecorded mail is delivered again").hasSize(4);
    }
}
