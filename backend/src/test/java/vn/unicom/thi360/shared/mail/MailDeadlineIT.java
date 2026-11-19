package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Hạn cứng của một lần gửi (R1-05, R2-01, R2-02, R2-03), với hạn thật ngắn để đi qua kẽ hở giữa
 * "DATA được nhận" và "đóng kết nối xong". Máy chủ kịch bản cung cấp vận chuyển thật tới socket,
 * nên mọi lần gửi đều chạy qua {@link SmtpMailTransport} thật.
 */
@TestPropertySource(properties = {"thi360.mail.attempt-timeout=PT3S", "thi360.mail.claim-lease=PT30S"})
class MailDeadlineIT extends AbstractMailIT {

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

            advance(Duration.ofMinutes(1));
            assertThat(dispatcher.dispatchBatch()).as("the retry delivers").isEqualTo(1);

            MailOutbox.Mail mail = outbox.require(to, MailKind.EMAIL_VERIFY);
            assertThat(mail.sentAt()).isEqualTo(START.plus(Duration.ofMinutes(1)));
            assertThat(mail.attempts()).isEqualTo(2);
            assertThat(server.dataAccepted.get()).as("exactly one accepted DATA, from the retry").isEqualTo(1);
            assertThat(server.dataCommands.get()).isEqualTo(2);
        }
    }
}
