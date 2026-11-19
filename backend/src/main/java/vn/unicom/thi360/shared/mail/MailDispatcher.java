package vn.unicom.thi360.shared.mail;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Gửi một lô thư (TM 4, plan ML), theo ba bước tách nhau để không gọi mạng khi giữ khóa dòng (B2):
 *
 * <ol>
 *   <li>transaction 1: lấy tối đa {@code batchSize} thư đến hạn bằng {@code FOR UPDATE SKIP LOCKED}, đẩy
 *       {@code next_attempt_at} ra {@code claimLease} rồi commit. Hai worker không lấy cùng một thư;
 *   <li>không transaction: gửi từng thư qua SMTP;
 *   <li>transaction mới cho mỗi thư: ghi kết quả (đã gửi, hẹn gửi lại, hoặc thất bại).
 * </ol>
 *
 * Tiến trình chết sau khi SMTP nhận thư mà chưa ghi kết quả thì thư được gửi lại khi hết hạn giữ chỗ:
 * gửi ít nhất một lần, không đòi đúng một lần.
 *
 * <p>Log chỉ ghi id thư, loại thư và mã lỗi; không ghi người nhận, thân thư, mã OTP.
 */
@Service
class MailDispatcher {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private final EmailMessageRepository repository;
    private final MailTransport transport;
    private final MailProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    MailDispatcher(
            EmailMessageRepository repository,
            MailTransport transport,
            MailProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transport = transport;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Một thư đã giữ chỗ, mang đủ dữ liệu để gửi mà không cần giữ phiên JPA. */
    private record Claimed(UUID id, String kind, String to, String subject, String html, String text) {
    }

    /** @return số thư đã gửi thành công trong lượt này */
    int dispatchBatch() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Mail dispatch must run outside a transaction");
        }
        String previousTrace = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String previousJob = MDC.get("job");
        String previousJobId = MDC.get("jobId");
        MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        MDC.put("job", "mail");
        MDC.put("jobId", Ids.newId().toString());
        try {
            List<Claimed> claimed = claim();
            int sent = 0;
            for (Claimed mail : claimed) {
                if (deliver(mail)) {
                    sent++;
                }
            }
            if (!claimed.isEmpty()) {
                jobLog.atInfo()
                        .addKeyValue("claimed", claimed.size())
                        .addKeyValue("sent", sent)
                        .log("Mail batch processed");
            }
            return sent;
        } finally {
            restore(TraceIdFilter.MDC_TRACE_ID, previousTrace);
            restore("job", previousJob);
            restore("jobId", previousJobId);
        }
    }

    private List<Claimed> claim() {
        Instant now = Instant.now(clock);
        return tx.execute(status -> {
            List<EmailMessage> due = repository.findDueForUpdate(properties.batchSize(), now);
            Instant heldUntil = now.plus(properties.claimLease());
            due.forEach(message -> message.claim(heldUntil));
            repository.saveAll(due);
            return due.stream()
                    .map(m -> new Claimed(m.getId(), m.getKind(), m.getToEmail(), m.getSubject(), m.getHtml(), m.getText()))
                    .toList();
        });
    }

    private boolean deliver(Claimed mail) {
        try {
            transport.send(mail.to(), mail.subject(), mail.html(), mail.text());
        } catch (Exception ex) {
            MailFailure failure = MailFailure.classify(ex);
            boolean recorded = record(mail, message -> {
                Instant at = Instant.now(clock);
                if (failure.permanent()) {
                    message.markFailedPermanently(failure.code(), at);
                } else {
                    message.recordFailure(failure.code(), at);
                }
            });
            if (recorded) {
                jobLog.atWarn()
                        .addKeyValue("mailId", mail.id())
                        .addKeyValue("kind", mail.kind())
                        .addKeyValue("permanent", failure.permanent())
                        .addKeyValue("error", failure.code())
                        .log("Mail send failed");
            }
            return false;
        }
        return record(mail, message -> message.markSent(Instant.now(clock)));
    }

    /** Ghi kết quả trong transaction mới; lỗi ghi không làm hỏng các thư còn lại của lô. */
    private boolean record(Claimed mail, Consumer<EmailMessage> update) {
        try {
            tx.executeWithoutResult(status -> repository.findById(mail.id()).ifPresent(update));
            return true;
        } catch (RuntimeException ex) {
            jobLog.atError()
                    .addKeyValue("mailId", mail.id())
                    .addKeyValue("kind", mail.kind())
                    .addKeyValue("error", "result-not-recorded " + ex.getClass().getSimpleName())
                    .log("Mail result could not be recorded");
            return false;
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
