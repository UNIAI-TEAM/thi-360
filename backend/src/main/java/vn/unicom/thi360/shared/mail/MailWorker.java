package vn.unicom.thi360.shared.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch của worker (profile {@code worker}): gửi thư mỗi 5 giây, dọn thư đã gửi mỗi ngày.
 * Cờ {@code thi360.mail.scheduling.enabled} tắt ở khối {@code test}. Chống chạy trùng nằm ở
 * {@code FOR UPDATE SKIP LOCKED} của {@link MailDispatcher}, không cần khóa ngoài (plan B5).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.mail.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MailWorker {

    private final MailDispatcher dispatcher;
    private final MailCleanupService cleanup;

    MailWorker(MailDispatcher dispatcher, MailCleanupService cleanup) {
        this.dispatcher = dispatcher;
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${thi360.mail.poll-delay:PT5S}")
    public void dispatch() {
        dispatcher.dispatchBatch();
    }

    @Scheduled(cron = "${thi360.mail.cleanup-cron:0 30 3 * * *}")
    public void cleanupSent() {
        cleanup.deleteExpired();
    }
}
