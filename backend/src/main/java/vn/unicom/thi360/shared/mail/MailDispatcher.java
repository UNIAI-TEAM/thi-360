package vn.unicom.thi360.shared.mail;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.EmailMessageStore.Claimed;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Gửi một lô thư (TM 4, plan ML). Mỗi thư đi qua ba bước tách nhau để không gọi mạng khi giữ khóa dòng (B2):
 *
 * <ol>
 *   <li>transaction ngắn: giữ chỗ một thư đến hạn bằng {@code FOR UPDATE SKIP LOCKED}, ghi mã giữ chỗ và đẩy
 *       {@code next_attempt_at} ra {@code claimLease}, rồi commit. Hai worker không lấy cùng một thư;
 *   <li>không transaction: gửi thư qua SMTP, tối đa hạn còn lại của giữ chỗ trừ một khoảng an toàn;
 *   <li>transaction mới: ghi kết quả, chỉ khi worker vẫn giữ đúng mã giữ chỗ.
 * </ol>
 *
 * <p>Giữ chỗ từng thư ngay trước khi gửi (không giữ cả lô) nên thời hạn giữ chỗ chỉ phải bao một lần gửi, mà
 * {@code claimLease} dài hơn {@code attemptTimeout}. Thư chưa tới lượt không bị giữ, nên worker chậm không làm
 * "treo" phần đuôi của lô. Nếu worker vẫn mất quyền giữ (tiến trình bị đứng quá hạn), kết quả của nó bị
 * từ chối, không đè lên kết quả của worker lấy lại.
 *
 * <p>Hàng rào ở ranh giới gửi (AD-19): <em>trong</em> việc gửi, trên luồng gửi, ngay trước lời gọi
 * {@link MailTransport#send}, worker kiểm tra lại trong cơ sở dữ liệu rằng nó vẫn giữ thư và chỗ chưa hết hạn
 * ({@link EmailMessageStore#stillHeld}), rồi mới lấy hạn còn lại ({@link SendDeadline}); không qua thì không
 * SMTP và ghi {@code claim-lost}. Hạn tuyệt đối {@code heldUntil - }{@link MailProperties#SAFETY_MARGIN}
 * tính một lần từ dòng giữ chỗ; mọi thời gian chờ (chờ kết quả, thời gian chờ kết nối/đọc/ghi của socket)
 * suy ra từ phần còn lại của hạn đó tại lúc dùng. Vì hạn dừng sớm hơn {@code heldUntil} một khoảng an toàn, một
 * worker khác chưa thể lấy lại thư trong lúc lần gửi này còn được phép chạy.
 *
 * <p>Phần còn lại, chấp nhận là ít-nhất-một-lần (at-least-once; quyết định Advisor ML:15 / AD-19): (1) worker
 * đứng cục bộ giữa hàng rào trong việc gửi và byte SMTP đầu tiên, kéo dài quá khoảng an toàn; (2) hạn giữ chỗ
 * trôi qua <em>trong lúc</em> phiên SMTP đang chạy, tối đa tới hạn tuyệt đối cộng khoảng an toàn; (3) tiến
 * trình chết sau khi SMTP nhận thư mà chưa ghi kết quả. Trong các trường hợp này thư có thể được gửi trùng; thiết kế
 * không hứa đúng-một-lần và không gia hạn giữ chỗ trong lúc gửi (không có heartbeat giữ chỗ).
 *
 * <p>Log chỉ ghi id thư, loại thư và mã lỗi; không ghi người nhận, thân thư, mã OTP.
 */
@Service
class MailDispatcher {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    /** Lần gửi bị bỏ vì tiến trình đang tắt: không ghi gì, giữ chỗ tự hết hạn. */
    private static final MailFailure ABANDONED = new MailFailure(false, "abandoned");

    /**
     * Lần gửi bị bỏ vì giữ chỗ đã hết hạn, bị worker khác lấy lại, hoặc phần hạn còn lại không đủ
     * để bắt đầu SMTP: không ghi gì, thư được lấy lại đúng hạn của nó.
     */
    private static final MailFailure CLAIM_LOST = new MailFailure(false, "claim-lost");

    private final EmailMessageStore store;
    private final MailTransport transport;
    private final MailProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    /**
     * Mỗi lần gửi chạy trên một luồng ảo riêng để hủy được khi quá hạn: ngắt luồng ảo đang chờ socket làm
     * đóng socket, còn luồng của worker thì không bị kẹt. Việc gửi mang theo {@code MDC} của lượt để log
     * trên luồng vận chuyển có đủ {@code traceId}/{@code job}/{@code jobId} (plan B15).
     */
    private final ExecutorService sendExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mail-send-", 0).factory());

    MailDispatcher(
            EmailMessageStore store,
            MailTransport transport,
            MailProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.store = store;
        this.transport = transport;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @PreDestroy
    void shutdown() {
        sendExecutor.shutdownNow();
    }

    /** @return số thư đã gửi thành công (và ghi được kết quả) trong lượt này */
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
            int claimed = 0;
            int sent = 0;
            while (claimed < properties.batchSize() && !Thread.currentThread().isInterrupted()) {
                Optional<Claimed> next = claimNext();
                if (next.isEmpty()) {
                    break;
                }
                claimed++;
                if (deliver(next.get())) {
                    sent++;
                }
            }
            if (claimed > 0) {
                jobLog.atInfo().addKeyValue("claimed", claimed).addKeyValue("sent", sent).log("Mail batch processed");
            }
            return sent;
        } finally {
            restore(TraceIdFilter.MDC_TRACE_ID, previousTrace);
            restore("job", previousJob);
            restore("jobId", previousJobId);
        }
    }

    private Optional<Claimed> claimNext() {
        Instant now = Instant.now(clock);
        Instant heldUntil = now.plus(properties.claimLease());
        UUID token = Ids.newId();
        return tx.execute(status -> store.claimNext(now, heldUntil, token));
    }

    private boolean deliver(Claimed mail) {
        MailFailure failure = send(mail);
        if (failure == ABANDONED || failure == CLAIM_LOST) {
            return false;
        }
        if (failure == null) {
            return record(mail, () -> store.markSent(mail, Instant.now(clock)));
        }
        Instant at = Instant.now(clock);
        int attemptsAfter = mail.attempts() + 1;
        boolean recorded;
        if (failure.permanent()) {
            recorded = record(mail, () -> store.markFailed(mail, failure.code(), at));
        } else if (MailRetrySchedule.hasRetryLeft(attemptsAfter)) {
            Instant retryAt = at.plus(MailRetrySchedule.delayAfter(attemptsAfter));
            recorded = record(mail, () -> store.markRetry(mail, failure.code(), retryAt));
        } else {
            recorded = record(mail, () -> store.markFailed(mail, failure.code(), at));
        }
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

    /**
     * Gửi một thư. Hạn tuyệt đối ({@link SendDeadline}) tính một lần từ dòng giữ chỗ. Kiểm tra quyền giữ
     * (mã giữ chỗ, chưa kết thúc, {@code next_attempt_at > now}) và kiểm tra hạn còn lại chạy <em>trong
     * việc gửi, trên luồng gửi</em>, ngay trước lời gọi vận chuyển; không qua thì không SMTP. Thời gian chờ
     * kết quả lấy từ phần hạn còn lại tại lúc chờ.
     *
     * @return {@code null} khi SMTP đã nhận thư, ngược lại lý do lỗi
     */
    private MailFailure send(Claimed mail) {
        SendDeadline deadline = new SendDeadline(clock, mail.heldUntil(), properties.attemptTimeout());
        if (deadline.expired()) {
            return skipped(mail);
        }
        Map<String, String> context = MDC.getCopyOfContextMap();
        Future<MailFailure> attempt = sendExecutor.submit(() -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                // Hàng rào: kiểm tra quyền giữ trước, rồi mới lấy hạn còn lại, để thời gian đứng ở
                // bước kiểm tra bị trừ vào hạn. Còn lại duy nhất là khoảng đứng giữa đây và byte SMTP đầu.
                if (!store.stillHeld(mail, Instant.now(clock)) || deadline.expired()) {
                    return CLAIM_LOST;
                }
                transport.send(mail.to(), mail.subject(), mail.html(), mail.text(), deadline);
                return null;
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        });
        try {
            MailFailure result = attempt.get(deadline.remaining().toNanos(), TimeUnit.NANOSECONDS);
            return result == CLAIM_LOST ? skipped(mail) : result;
        } catch (TimeoutException ex) {
            attempt.cancel(true);
            return MailFailure.timeout();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof SendDeadline.ExpiredException) {
                return skipped(mail);
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            return MailFailure.classify(ex.getCause());
        } catch (InterruptedException ex) {
            // Đang tắt: bỏ lần gửi, giữ nguyên giữ chỗ để hết hạn rồi gửi lại.
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            return ABANDONED;
        }
    }

    private static MailFailure skipped(Claimed mail) {
        jobLog.atWarn()
                .addKeyValue("mailId", mail.id())
                .addKeyValue("kind", mail.kind())
                .addKeyValue("error", "claim-lost")
                .log("Mail send skipped: the claim expired or was taken before SMTP started");
        return CLAIM_LOST;
    }

    /**
     * Ghi kết quả trong transaction mới. Lỗi ghi không làm hỏng các thư còn lại của lô: thư đó giữ nguyên
     * giữ chỗ và được lấy lại khi hết hạn. Worker đã mất quyền giữ thì không ghi gì.
     *
     * @return {@code true} khi kết quả đã được ghi
     */
    private boolean record(Claimed mail, BooleanSupplier write) {
        try {
            Boolean written = tx.execute(status -> write.getAsBoolean());
            if (!Boolean.TRUE.equals(written)) {
                jobLog.atWarn()
                        .addKeyValue("mailId", mail.id())
                        .addKeyValue("kind", mail.kind())
                        .addKeyValue("error", "claim-lost")
                        .log("Mail result discarded: another worker holds the mail");
                return false;
            }
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
