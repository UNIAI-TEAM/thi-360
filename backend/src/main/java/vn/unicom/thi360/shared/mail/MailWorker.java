package vn.unicom.thi360.shared.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch của worker (profile {@code worker}): gửi thư mỗi {@code thi360.mail.poll-delay} (5 giây), dọn thư đã gửi
 * theo {@code thi360.mail.cleanup-cron} (mỗi ngày; {@code "-"} tắt riêng việc dọn). Cờ
 * {@code thi360.mail.scheduling.enabled} tắt ở khối {@code test}. Chống chạy trùng giữa các worker nằm ở
 * {@code FOR UPDATE SKIP LOCKED} và mã giữ chỗ của {@link MailDispatcher}, không cần khóa ngoài (plan B5).
 *
 * <p>Hai lịch chạy trên bộ lập lịch riêng {@code mailTaskScheduler} (đăng ký bởi
 * {@link MailSchedulerRegistrar}), không dùng bộ lập lịch mặc định của Spring Boot: bộ đó chỉ có một
 * luồng, dùng chung với outbox, audit, tài khoản; một máy chủ SMTP chậm không được chặn các việc đó.
 * Bean {@code taskScheduler} mặc định của Boot vẫn giữ nguyên cho mọi {@code @Scheduled} khác.
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

    @Scheduled(
            fixedDelayString = "${thi360.mail.poll-delay:" + MailProperties.DEFAULT_POLL_DELAY + "}",
            scheduler = MailSchedulerRegistrar.SCHEDULER_BEAN)
    public void dispatch() {
        dispatcher.dispatchBatch();
    }

    @Scheduled(
            cron = "${thi360.mail.cleanup-cron:" + MailProperties.DEFAULT_CLEANUP_CRON + "}",
            scheduler = MailSchedulerRegistrar.SCHEDULER_BEAN)
    public void cleanupSent() {
        cleanup.deleteExpired();
    }
}
