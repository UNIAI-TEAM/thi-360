package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import vn.unicom.thi360.support.MailOutbox;

class MailQueueIT extends AbstractMailIT {

    @Autowired
    private EmailMessageRepository repository;

    @Test
    void rollbackLeavesNoMail() {
        String markedRollback = MailSamples.randomAddress();
        tx.executeWithoutResult(status -> {
            queue.enqueue(MailKind.EMAIL_VERIFY, markedRollback, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null);
            repository.flush();
            assertThat(outbox.count(markedRollback)).as("the mail is really written before the rollback").isEqualTo(1);
            status.setRollbackOnly();
        });

        String failedUseCase = MailSamples.randomAddress();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    queue.enqueue(
                            MailKind.PASSWORD_RESET, failedUseCase, "en", MailSamples.model(MailKind.PASSWORD_RESET), null);
                    throw new IllegalStateException("use case failed after enqueue");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(outbox.count(markedRollback)).isZero();
        assertThat(outbox.count(failedUseCase)).isZero();
    }

    @Test
    void enqueueRequiresTransaction() {
        String to = MailSamples.randomAddress();

        assertThatThrownBy(() ->
                        queue.enqueue(MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outbox.count(to)).isZero();
    }

    @Test
    void missingModelKeyIsRejected() {
        for (MailKind kind : MailKind.values()) {
            for (String missing : kind.requiredModelKeys()) {
                String to = MailSamples.randomAddress();
                Map<String, Object> model = new HashMap<>(MailSamples.model(kind));
                model.remove(missing);

                assertThatThrownBy(() -> tx.executeWithoutResult(
                                status -> queue.enqueue(kind, to, "vi", model, null)))
                        .as("%s without '%s'", kind, missing)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(missing);
                assertThat(outbox.count(to)).as("%s without '%s' leaves no row", kind, missing).isZero();
            }
        }
    }

    @Test
    void invalidRecipientIsRejected() {
        for (String bad : new String[] {null, "", "  ", "not-an-address"}) {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> queue.enqueue(
                            MailKind.EMAIL_VERIFY, bad, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void enqueueStoresRenderedMailAndUserForLaterDelivery() {
        String to = MailSamples.randomAddress();
        UUID userId = UUID.randomUUID();

        UUID id = tx.execute(status -> queue.enqueue(
                MailKind.EMAIL_VERIFY, to, "en-US", MailSamples.model(MailKind.EMAIL_VERIFY, "551177"), userId));

        MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
        assertThat(mail.id()).isEqualTo(id);
        assertThat(mail.locale()).isEqualTo("en");
        assertThat(mail.userId()).isEqualTo(userId);
        assertThat(mail.subject()).isNotBlank();
        assertThat(mail.html()).contains("551177");
        assertThat(mail.text()).contains("551177").doesNotContain(mail.subject());
        assertThat(mail.attempts()).isZero();
        assertThat(mail.sentAt()).isNull();
        assertThat(mail.failedAt()).isNull();
        assertThat(mail.nextAttemptAt()).isEqualTo(mail.createdAt());
    }

    @Test
    void mailOutboxReadsLatestByRecipientAndKind() {
        String to = MailSamples.randomAddress();
        String other = MailSamples.randomAddress();

        // Hai thư cùng người nhận, cùng loại, cùng một thời điểm của đồng hồ: thư xếp sau phải thắng.
        UUID first = enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        UUID second = enqueue(MailKind.EMAIL_VERIFY, "vi", to);
        advance(Duration.ofSeconds(5));
        UUID third = enqueue(MailKind.EMAIL_VERIFY, "en", to);
        UUID otherKind = enqueue(MailKind.PASSWORD_RESET, "vi", to);
        UUID otherRecipient = enqueue(MailKind.EMAIL_VERIFY, "vi", other);

        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).id()).isEqualTo(third);
        assertThat(outbox.all(to, MailKind.EMAIL_VERIFY)).extracting(MailOutbox.Mail::id)
                .containsExactly(third, second, first);
        assertThat(outbox.require(to, MailKind.PASSWORD_RESET).id()).isEqualTo(otherKind);
        assertThat(outbox.require(other, MailKind.EMAIL_VERIFY).id()).isEqualTo(otherRecipient);
        assertThat(outbox.latest(to, MailKind.REFUND_DONE)).isEmpty();
        assertThat(outbox.latest(MailSamples.randomAddress(), MailKind.EMAIL_VERIFY)).isEmpty();
        assertThat(outbox.require(to.toUpperCase(), MailKind.PASSWORD_RESET).id()).isEqualTo(otherKind);
        assertThat(outbox.count(to)).isEqualTo(4);
    }
}
