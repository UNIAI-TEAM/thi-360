package vn.unicom.thi360.shared.mail;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Lịch của worker (profile {@code worker}): gửi thư mỗi {@code thi360.mail.poll-delay} (5 giây), dọn thư đã gửi
 * theo {@code thi360.mail.cleanup-cron} (mỗi ngày). Cờ {@code thi360.mail.scheduling.enabled} tắt ở khối {@code test}.
 * Chống chạy trùng giữa các worker nằm ở {@code FOR UPDATE SKIP LOCKED} và mã giữ chỗ của {@link MailDispatcher},
 * không cần khóa ngoài (plan B5).
 *
 * <p>Worker có bộ lập lịch riêng, không dùng {@code @Scheduled}: bộ lập lịch mặc định của Spring Boot chỉ có một
 * luồng, dùng chung với outbox, audit, tài khoản; một máy chủ SMTP chậm không được chặn các việc đó. Bộ lập lịch này
 * không là bean (kẻo Spring Boot bỏ bộ lập lịch mặc định đi), và {@code fixedDelay} đảm bảo trên một tiến trình
 * không có hai lượt gửi chạy chồng nhau.
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.mail.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MailWorker {

    private final MailDispatcher dispatcher;
    private final MailCleanupService cleanup;
    private final MailProperties properties;
    private ThreadPoolTaskScheduler scheduler;

    MailWorker(MailDispatcher dispatcher, MailCleanupService cleanup, MailProperties properties) {
        this.dispatcher = dispatcher;
        this.cleanup = cleanup;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("mail-worker-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::dispatch, properties.pollDelay());
        scheduler.schedule(this::cleanupSent, new CronTrigger(properties.cleanupCron()));
    }

    @PreDestroy
    void stop() {
        scheduler.shutdown();
    }

    void dispatch() {
        dispatcher.dispatchBatch();
    }

    void cleanupSent() {
        cleanup.deleteExpired();
    }
}
